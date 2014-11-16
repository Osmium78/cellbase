package org.opencb.cellbase.server.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Splitter;
import com.wordnik.swagger.annotations.ApiParam;
import org.opencb.cellbase.core.common.Species;
import org.opencb.cellbase.core.common.core.CellbaseConfiguration;
import org.opencb.cellbase.core.lib.DBAdaptorFactory;
import org.opencb.cellbase.core.lib.api.ChromosomeDBAdaptor;
import org.opencb.cellbase.core.lib.dbquery.QueryOptions;
import org.opencb.cellbase.core.lib.dbquery.QueryResult;
import org.opencb.cellbase.lib.mongodb.db.MongoDBAdaptorFactory;
import org.opencb.cellbase.server.QueryResponse;
import org.opencb.cellbase.server.exception.SpeciesException;
import org.opencb.cellbase.server.exception.VersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.ResponseBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

@Path("/{version}/{species}")
@Produces("text/plain")
public class GenericRestWSServer implements IWSServer {

    // Common application parameters
    @DefaultValue("")
    @PathParam("version")
    @ApiParam(name = "version", value = "Excluded fields will not be returned. Comma separated JSON paths must be provided",
            defaultValue = "v3")
    protected String version;

    @DefaultValue("")
    @PathParam("species")
    @ApiParam(name = "species", value = "Excluded fields will not be returned. Comma separated JSON paths must be provided",
            defaultValue = "hsapiens", allowableValues = "hsapiens,mmusculus")
    protected String species;

    protected String assembly = null;
    protected UriInfo uriInfo;
    protected HttpServletRequest httpServletRequest;

    protected QueryOptions queryOptions;

    // file name without extension which server will give back when file format is !null
    private String filename;

    @DefaultValue("")
    @QueryParam("exclude")
    @ApiParam(name = "excluded fields", value = "Excluded fields will not be returned. Comma separated JSON paths must be provided")
    protected String exclude;

    @DefaultValue("")
    @QueryParam("include")
    @ApiParam(name = "included fields", value = "Included fields are the only to be returned. Comma separated JSON path must be provided")
    protected String include;

    @DefaultValue("-1")
    @QueryParam("limit")
    @ApiParam(name = "limit", value = "Max number of results to be returned. No limit applied when -1 [-1]")
    protected int limit;

    @DefaultValue("-1")
    @QueryParam("skip")
    @ApiParam(name = "skip", value = "Number of results to be skipped. No skip applied when -1 [-1]")
    protected int skip;

    @DefaultValue("false")
    @QueryParam("count")
    @ApiParam(name = "count", value = "The total number of results is returned [false]")
    protected String count;

    @DefaultValue("json")
    @QueryParam("of")
    @ApiParam(name = "Output format", value = "Output format, Protobuf is not yet implemented", defaultValue = "json", allowableValues = "json,pb (Not implemented yet)")
    protected String outputFormat;

    protected static ObjectMapper jsonObjectMapper;
    protected static ObjectWriter jsonObjectWriter;

    protected long startTime;
    protected long endTime;
    protected QueryResponse queryResponse;

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Loading properties file just one time to be more efficient. All methods
     * will check parameters so to avoid extra operations this config can load
     * versions and species
     */
    protected static Properties properties;
    protected static CellbaseConfiguration config = new CellbaseConfiguration();


    /**
     *  Species for each version
     */
    static {
        InputStream is = GenericRestWSServer.class.getClassLoader().getResourceAsStream("application.properties");
        properties = new Properties();
        try {
            properties.load(is);
            if (properties != null) {
                config.setCoreChunkSize(Integer.parseInt(properties.getProperty("CORE_CHUNK_SIZE", "5000")));
                config.setVariationChunkSize(Integer.parseInt(properties.getProperty("VARIATION_CHUNK_SIZE", "1000")));
                config.setGenomeSequenceChunkSize(Integer.parseInt(properties.getProperty("CELLBASE.GENOME_SEQUENCE.CHUNK_SIZE", "2000")));
                config.setConservedRegionChunkSize(Integer.parseInt(properties.getProperty("CELLBASE.CONSERVED_REGION.CHUNK_SIZE", "2000")));
                config.setVersion(properties.getProperty("CELLBASE.VERSION"));

                if(properties.containsKey("CELLBASE.AVAILABLE.SPECIES")) {
                    String[] speciesArray = properties.getProperty("CELLBASE.AVAILABLE.SPECIES").split(",");
                    String[] alias = null;
                    String[] assemblies;
                    String assemblyPrefix;
                    String dbConfigurationId;
                    for (String species : speciesArray) {
                        species = species.toUpperCase();
                        if(properties.containsKey(species+".ASSEMBLY")) {
                            assemblies = properties.getProperty(species+".ASSEMBLY").split(",");
                            for(String assembly : assemblies) {
                                System.out.println("WS assembly = " + assembly);
                                assembly = assembly.toUpperCase();
                                assemblyPrefix = species + "." + assembly;

                                dbConfigurationId = properties.getProperty(assemblyPrefix + ".DB");
                                System.out.println("WS dbConfigurationId = " + dbConfigurationId);
                                config.addSpeciesConnection(species, assembly,
                                        properties.getProperty(dbConfigurationId + ".HOST"),
                                        properties.getProperty(assemblyPrefix + ".DATABASE"),
                                        Integer.parseInt(properties.getProperty(dbConfigurationId + ".PORT")),
                                        properties.getProperty(dbConfigurationId + ".DRIVER_CLASS"),
                                        properties.getProperty(dbConfigurationId + ".USERNAME"),
                                        properties.getProperty(dbConfigurationId + ".PASSWORD"),
                                        Integer.parseInt(properties.getProperty(dbConfigurationId + ".MAX_POOL_SIZE", "10")),
                                        Integer.parseInt(properties.getProperty(dbConfigurationId + ".TIMEOUT")));
                                config.addSpeciesInfo(species, assembly, properties.getProperty(species + ".TAXONOMY"));
                            }
                        } else {
                            dbConfigurationId = properties.getProperty(species + ".DB");
                            config.addSpeciesConnection(species,
                                    properties.getProperty(dbConfigurationId + ".HOST"),
                                    properties.getProperty(species + ".DB"),
                                    Integer.parseInt(properties.getProperty(dbConfigurationId + ".PORT")),
                                    properties.getProperty(dbConfigurationId + ".DRIVER_CLASS"),
                                    properties.getProperty(dbConfigurationId + ".USERNAME"),
                                    properties.getProperty(dbConfigurationId + ".PASSWORD"),
                                    Integer.parseInt(properties.getProperty(dbConfigurationId + ".MAX_POOL_SIZE", "10")),
                                    Integer.parseInt(properties.getProperty(dbConfigurationId + ".TIMEOUT")));
                            config.addSpeciesInfo(species, properties.getProperty(species + ".TAXONOMY"));
                        }
                        alias = properties.getProperty(species + ".ALIAS").split(",");
                        for (String al : alias) {
                            config.addSpeciesAlias(al, species);
                        }
                        // For to recognize the species code
                        config.addSpeciesAlias(species, species);
                    }
                }
//                if(properties.containsKey("CELLBASE.AVAILABLE.VERSIONS")) {
//                    // read all versions available
//                    List<String> versionList = Splitter.on(",").splitToList(properties.getProperty("CELLBASE.AVAILABLE.VERSIONS"));
//                    HashSet<String> currAvailableSet = new HashSet<String>();
//                    if (versionList != null) {
//                        for (String version : versionList) {
//                            String versionKey = "CELLBASE." + version.toUpperCase() + ".AVAILABLE.SPECIES";
//                            if (properties.containsKey(versionKey)) {
//                                // read the species available for each version
//                                List<String> speciesList = Splitter.on(",").splitToList(properties.getProperty(versionKey));
//                                if (speciesList != null) {
//                                    for (String species : speciesList) {
//                                        currAvailableSet.add(species.trim());
//                                    }
//                                }
//                            }
//                            config.addAvailableVersionSpeciesMap(version.trim(), currAvailableSet);
//                            currAvailableSet.clear();
//
//                        }
//                    }
//                }
            }

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    /**
     * DBAdaptorFactory creation, this object can be initialize with an
     * HibernateDBAdaptorFactory or an HBaseDBAdaptorFactory. This object is a
     * factory for creating adaptors like GeneDBAdaptor
     */
    protected static DBAdaptorFactory dbAdaptorFactory;

    static {
        // BasicConfigurator.configure();
        // dbAdaptorFactory = new HibernateDBAdaptorFactory();
        dbAdaptorFactory = new MongoDBAdaptorFactory(config);
        System.out.println("Static block #1");

        jsonObjectMapper = new ObjectMapper();
        jsonObjectWriter = jsonObjectMapper.writer();
    }

    public GenericRestWSServer(@PathParam("version") String version,
                               @PathParam("species") String species,
                               @Context UriInfo uriInfo, @Context HttpServletRequest hsr) throws VersionException, IOException {


        this.version = version.toUpperCase();;
        this.species = species;
        this.uriInfo = uriInfo;
        this.httpServletRequest = hsr;

        init(version, species, uriInfo);

        logger.info("GenericrestWSServer: in 'constructor'");
    }

    protected void init(String version, String species, UriInfo uriInfo) throws VersionException, IOException {

        startTime = System.currentTimeMillis();
        queryResponse = new QueryResponse();

        // load properties file
        // ResourceBundle databaseConfig =
        // ResourceBundle.getBundle("org.bioinfo.infrared.ws.application");
        // config = new Config(databaseConfig);

        // mediaType = MediaType.valueOf("text/plain");
        queryOptions = new QueryOptions();
        // logger = new Logger();
        // logger.setLevel(Logger.DEBUG_LEVEL);
        logger.info("GenericrestWSServer: in 'init' method");

        System.out.println("uriInfo.getQueryParameters(): "+uriInfo.getQueryParameters());
    }

    /**
     * Overriden methods
     */
    @Override
    public void checkParams() throws VersionException, SpeciesException {
        if (version == null) {
            throw new VersionException("Version not valid: '" + version + "'");
        }
        if (species == null) {
            throw new SpeciesException("Species not valid: '" + species + "'");
        }

        /**
         * Check version parameter, must be: v1, v2, ... If 'latest' then is
         * converted to appropriate version
         */
//        // TODO uncomment
//        if (version != null && version.equals("latest") && config.getProperty("CELLBASE.LATEST.VERSION") != null) {
//            version = config.getProperty("CELLBASE.LATEST.VERSION");
//            System.out.println("version: " + version);
//        }

//        if (availableVersionSpeciesMap.containsKey(version)) {
//            if (!availableVersionSpeciesMap.get(version).contains(species)) {
        if(!config.getVersion().equals(this.version)){
            System.out.println("config = " + config.getVersion());
            throw new VersionException("Version not valid: '" + version + "'");
        }

//        parseCommonQueryParameters(uriInfo.getQueryParameters());
        MultivaluedMap<String, String> multivaluedMap = uriInfo.getQueryParameters();
        queryOptions.put("metadata", (multivaluedMap.get("metadata") != null) ? multivaluedMap.get("metadata").get(0).equals("true") : true);

        queryOptions.put("exclude", (exclude != null && !exclude.equals("")) ? Splitter.on(",").splitToList(exclude) : null);
        queryOptions.put("include", (include != null && !include.equals("")) ? Splitter.on(",").splitToList(include) : null);
        queryOptions.put("limit", (limit > 0) ? limit : -1);
        queryOptions.put("skip", (skip > 0) ? skip : -1);
        queryOptions.put("count", (count != null && !count.equals("")) ? Boolean.parseBoolean(count) : false);

        outputFormat = (outputFormat != null && !outputFormat.equals("")) ? outputFormat : "json";
        filename = (multivaluedMap.get("filename") != null) ? multivaluedMap.get("filename").get(0) : "result";
    }

    @Override
    public String stats() {
        return null;
    }

    @GET
    @Path("/help")
    public Response help() {
        return createOkResponse("No help available");
    }

    /**
     * Auxiliar methods
     */
    @GET
    @Path("/{species}")
    public Response getCategories(@PathParam("species") String species) {
        if (isSpecieAvailable(species)) {
            return createOkResponse("feature\ngenomic\nnetwork\nregulatory");
        }
        return getSpecies();
    }

    @GET
    @Path("/{species}/{category}")
    public Response getCategory(@PathParam("species") String species, @PathParam("category") String category) {
        if (isSpecieAvailable(species)) {
            if ("feature".equalsIgnoreCase(category)) {
                return createOkResponse("exon\ngene\nkaryotype\nprotein\nsnp\ntranscript");
            }
            if ("genomic".equalsIgnoreCase(category)) {
                return createOkResponse("position\nregion\nvariant");
            }
            if ("network".equalsIgnoreCase(category)) {
                return createOkResponse("pathway");
            }
            if ("regulatory".equalsIgnoreCase(category)) {
                return createOkResponse("mirna_gene\nmirna_mature\ntf");
            }
            return createOkResponse("feature\ngenomic\nnetwork\nregulatory");
        } else {
            return getSpecies();
        }
    }

    @GET
    @Path("/{species}/{category}/{subcategory}")
    public Response getSubcategory(@PathParam("species") String species, @PathParam("category") String category,
                                   @PathParam("subcategory") String subcategory) {
        return getCategory(species, category);
    }

    @GET
    @Path("/version")
    public Response getVersion() {
        StringBuilder versionMessage = new StringBuilder();
        versionMessage.append("Homo sapiens").append("\t").append("Ensembl 64").append("\n");
        versionMessage.append("Mus musculus").append("\t").append("Ensembl 65").append("\n");
        versionMessage.append("Rattus norvegicus").append("\t").append("Ensembl 65").append("\n");
        versionMessage.append("Drosophila melanogaster").append("\t").append("Ensembl 65").append("\n");
        versionMessage.append("Canis familiaris").append("\t").append("Ensembl 65").append("\n");
        versionMessage.append("...").append("\n\n");
        versionMessage
                .append("The rest of nfo will be added soon, sorry for the inconveniences. You can find mor info at:")
                .append("\n\n").append("http://docs.bioinfo.cipf.es/projects/variant/wiki/Databases");
        return createOkResponse(versionMessage.toString(), MediaType.valueOf("text/plain"));
    }

    @GET
    @Path("/species")
    public Response getSpecies() {
        List<Species> speciesList = getSpeciesList();
        MediaType mediaType = MediaType.valueOf("application/javascript");
        if (uriInfo.getQueryParameters().get("of") != null
                && uriInfo.getQueryParameters().get("of").get(0).equalsIgnoreCase("json")) {
            try {
                return createOkResponse(jsonObjectWriter.writeValueAsString(speciesList), mediaType);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            for (Species sp : speciesList) {
                stringBuilder.append(sp.toString()).append("\n");
            }
            mediaType = MediaType.valueOf("text/plain");
            return createOkResponse(stringBuilder.toString(), mediaType);
        }
    }

    @GET
    @Path("/{species}/i")
    public Response getSpeciesInfo2(@PathParam("species") String species, @DefaultValue("json") @QueryParam("of") String of) {
        ChromosomeDBAdaptor chromosomeDBAdaptor = dbAdaptorFactory.getChromosomeDBAdaptor(species, this.assembly);
        return createOkResponse(chromosomeDBAdaptor.speciesInfoTmp(species, queryOptions));
    }

    @GET
    @Path("/speciesinfo")
    public Response getSpeciesInfo() {
        List<String> speciesList = new ArrayList<>(3);
        speciesList.add("Homo sapiens");
        speciesList.add("Mus musculus");
        speciesList.add("Rattus norvegicus");

        List<QueryResult> queryResults = new ArrayList<>(speciesList.size());
        for(String specie: speciesList) {
            ChromosomeDBAdaptor chromosomeDBAdaptor = dbAdaptorFactory.getChromosomeDBAdaptor(specie, this.assembly);
            queryResults.add(chromosomeDBAdaptor.speciesInfoTmp(specie, queryOptions));

        }
        return createOkResponse(queryResults);
    }

    @GET
    @Path("/{species}/chromosomes")
    public Response getChromosomes(@PathParam("species") String species) {
        return null;
//        // TODO uncomment
//        return createOkResponse(config.getProperty("CELLBASE." + species.toUpperCase() + ".CHROMOSOMES"),
//                MediaType.valueOf("text/plain"));
    }

    @GET
    @Path("/echo/{msg}")
    public Response echo(@PathParam("msg") String msg) {
        logger.info(msg);
        logger.warn(msg);
        logger.debug(msg);
        logger.error(msg);
        return createStringResponse(msg);
    }


    @Deprecated
    protected Response generateResponse(String queryString, List features) throws IOException {
        return createOkResponse("TODO: generateResponse is drepecated");
    }

    @Deprecated
    protected Response generateResponse(String queryString, String headerTag, List features) throws IOException {
        return createOkResponse("TODO: generateResponse is drepecated");
    }


    @Deprecated
//    protected Response createResponse(String response, MediaType mediaType) throws IOException {
//        logger.debug("CellBase - CreateResponse, QueryParams: FileFormat => " + fileFormat + ", OutputFormat => " + outputFormat + ", Compress => " + outputCompress);
//        logger.debug("CellBase - CreateResponse, Inferred media type: " + mediaType.toString());
//        logger.debug("CellBase - CreateResponse, Response: " + ((response.length() > 50) ? response.substring(0, 49) + "..." : response));
//
//        if (fileFormat == null || fileFormat.equalsIgnoreCase("")) {
//            if (outputCompress != null && outputCompress.equalsIgnoreCase("true")
//                    && !outputFormat.equalsIgnoreCase("jsonp") && !outputFormat.equalsIgnoreCase("jsontext")) {
//                response = Arrays.toString(StringUtils.gzipToBytes(response)).replace(" ", "");
//            }
//        } else {
//            mediaType = MediaType.APPLICATION_OCTET_STREAM_TYPE;
//            logger.debug("\t\t - Creating byte stream ");
//
//            if (outputCompress != null && outputCompress.equalsIgnoreCase("true")) {
//                OutputStream bos = new ByteArrayOutputStream();
//                bos.write(response.getBytes());
//
//                ZipOutputStream zipstream = new ZipOutputStream(bos);
//                zipstream.setLevel(9);
//
//                logger.debug("CellBase - CreateResponse, zipping... Final media Type: " + mediaType.toString());
//
//                return this.createOkResponse(zipstream, mediaType, filename + ".zip");
//
//            } else {
//                if (fileFormat.equalsIgnoreCase("xml")) {
//                    // mediaType = MediaType.valueOf("application/xml");
//                }
//
//                if (fileFormat.equalsIgnoreCase("excel")) {
//                    // mediaType =
//                    // MediaType.valueOf("application/vnd.ms-excel");
//                }
//                if (fileFormat.equalsIgnoreCase("txt") || fileFormat.equalsIgnoreCase("text")) {
//                    logger.debug("\t\t - text File ");
//
//                    byte[] streamResponse = response.getBytes();
//                    // return Response.ok(streamResponse,
//                    // mediaType).header("content-disposition","attachment; filename = "+
//                    // filename + ".txt").build();
//                    return this.createOkResponse(streamResponse, mediaType, filename + ".txt");
//                }
//            }
//        }
//        logger.debug("CellBase - CreateResponse, Final media Type: " + mediaType.toString());
//        // return Response.ok(response, mediaType).build();
//        return this.createOkResponse(response, mediaType);
//    }

    protected Response createErrorResponse(String method, String errorMessage) {
        if (!errorMessage.contains("Species") && !errorMessage.contains("Version")) {
            // StringBuilder message = new StringBuilder();
            // message.append("URI: "+uriInfo.getAbsolutePath().toString()).append("\n");
            // message.append("Method: "+httpServletRequest.getMethod()+" "+method).append("\n");
            // message.append("Message: "+errorMessage).append("\n");
            // message.append("Remote Addr: http://ipinfodb.com/ip_locator.php?ip="+httpServletRequest.getRemoteAddr()).append("\n");
            // HttpUtils.send("correo.cipf.es", "fsalavert@cipf.es",
            // "babelomics@cipf.es", "Infrared error notice",
            // message.toString());
        }
        if (outputFormat.equalsIgnoreCase("json")) {
            try {
                return buildResponse(Response.ok(jsonObjectWriter.writeValueAsString(new HashMap<>().put("error", errorMessage)), MediaType.APPLICATION_JSON_TYPE));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        } else {
            String error = "An error occurred: " + errorMessage;
            return buildResponse(Response.ok(error, MediaType.valueOf("text/plain")));
        }
        return null;
    }

    protected Response createErrorResponse(Object o) {
        String objMsg = o.toString();
        if (objMsg.startsWith("ERROR:")) {
            return buildResponse(Response.ok("" + o));
        } else {
            return buildResponse(Response.ok("ERROR: " + o));
        }
    }

    protected Response createOkResponse(Object obj) {
        switch (outputFormat.toLowerCase()) {
            case "json":
                return createJsonResponse(obj);
            case "xml":
                return createOkResponse(obj, MediaType.APPLICATION_XML_TYPE);
            default:
                return buildResponse(Response.ok(obj));
        }
    }

    protected Response createJsonResponse(Object obj) {
        endTime = System.currentTimeMillis() - startTime;
        queryResponse.put("time", endTime);
//        queryResponse.put("version", version);
//        queryResponse.put("species", species);
        queryResponse.put("queryOptions", queryOptions);
        queryResponse.put("response", obj);

        // Quick fix to be copliant with new standard
        queryResponse.put("error", queryResponse.get("errorMsg"));
        queryResponse.put("warning", queryResponse.get("warningMsg"));
        queryResponse.remove("errorMsg");
        queryResponse.remove("warningMsg");
        queryResponse.remove("dbVersion");

        try {
            return buildResponse(Response.ok(jsonObjectWriter.writeValueAsString(queryResponse), MediaType.APPLICATION_JSON_TYPE));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            logger.error("Error parsing queryResponse object");
            return null;
        }
    }

    protected Response createOkResponse(Object obj, MediaType mediaType) {
        return buildResponse(Response.ok(obj, mediaType));
    }

    protected Response createOkResponse(Object obj, MediaType mediaType, String fileName) {
        return buildResponse(Response.ok(obj, mediaType).header("content-disposition", "attachment; filename =" + fileName));
    }

    protected Response createStringResponse(String str) {
        return buildResponse(Response.ok(str));
    }

    private Response buildResponse(ResponseBuilder responseBuilder) {
        return responseBuilder.header("Access-Control-Allow-Origin", "*").build();
    }

    @GET
    public Response getHelp() {
        return getSpecies();
    }

    private List<Species> getSpeciesList() {
        List<Species> speciesList = new ArrayList<Species>(11);
        speciesList.add(new Species("hsa", "human", "Homo sapiens", "GRCh37.p7"));
        speciesList.add(new Species("mmu", "mouse", "Mus musculus", "NCBIM37"));
        speciesList.add(new Species("rno", "rat", "Rattus norvegicus", "RGSC 3.4"));
        speciesList.add(new Species("dre", "zebrafish", "Danio rerio", "Zv9"));
        speciesList.add(new Species("cel", "worm", "Caenorhabditis elegans", "WS230"));
        speciesList.add(new Species("dme", "fruitfly", "Drosophila melanogaster", "BDGP 5.39"));
        speciesList.add(new Species("sce", "yeast", "Saccharomyces cerevisiae", "EF 4"));
        speciesList.add(new Species("cfa", "dog", "Canis familiaris", "CanFam 2.0"));
        speciesList.add(new Species("ssc", "pig", "Sus scrofa", "Sscrofa10.2"));
        speciesList.add(new Species("aga", "mosquito", "Anopheles gambiae", "AgamP3"));
        speciesList.add(new Species("pfa", "malaria parasite", "Plasmodium falciparum", "3D7"));

        speciesList.add(new Species("hsapiens", "", "", ""));
        speciesList.add(new Species("mmusculus", "", "", ""));
        speciesList.add(new Species("rnorvegicus", "", "", ""));
        speciesList.add(new Species("ptroglodytes", "", "", ""));
        speciesList.add(new Species("ggorilla", "", "", ""));
        speciesList.add(new Species("pabelii", "", "", ""));
        speciesList.add(new Species("mmulatta", "", "", ""));
        speciesList.add(new Species("sscrofa", "", "", ""));
        speciesList.add(new Species("cfamiliaris", "", "", ""));
        speciesList.add(new Species("ecaballus", "", "", ""));
        speciesList.add(new Species("ocuniculus", "", "", ""));
        speciesList.add(new Species("ggallus", "", "", ""));
        speciesList.add(new Species("btaurus", "", "", ""));
        speciesList.add(new Species("fcatus", "", "", ""));
        speciesList.add(new Species("drerio", "", "", ""));
        speciesList.add(new Species("cintestinalis", "", "", ""));
        speciesList.add(new Species("dmelanogaster", "", "", ""));
        speciesList.add(new Species("dsimulans", "", "", ""));
        speciesList.add(new Species("dyakuba", "", "", ""));
        speciesList.add(new Species("agambiae", "", "", ""));
        speciesList.add(new Species("celegans", "", "", ""));
        speciesList.add(new Species("scerevisiae", "", "", ""));
        speciesList.add(new Species("spombe", "", "", ""));
        speciesList.add(new Species("afumigatus", "", "", ""));
        speciesList.add(new Species("aniger", "", "", ""));
        speciesList.add(new Species("anidulans", "", "", ""));
        speciesList.add(new Species("aoryzae", "", "", ""));
        speciesList.add(new Species("pfalciparum", "", "", ""));
        speciesList.add(new Species("lmajor", "", "", ""));
        speciesList.add(new Species("athaliana", "", "", ""));
        speciesList.add(new Species("alyrata", "", "", ""));
        speciesList.add(new Species("bdistachyon", "", "", ""));
        speciesList.add(new Species("osativa", "", "", ""));
        speciesList.add(new Species("gmax", "", "", ""));
        speciesList.add(new Species("vvinifera", "", "", ""));
        speciesList.add(new Species("zmays", "", "", ""));

        return speciesList;
    }

    /**
     * TO DELETE
     */
    @Deprecated
    private boolean isSpecieAvailable(String species) {
        List<Species> speciesList = getSpeciesList();
        for (int i = 0; i < speciesList.size(); i++) {
            // This only allows to show the information if species is in 3
            // letters format
            if (species.equalsIgnoreCase(speciesList.get(i).getSpecies())) {
                return true;
            }
        }
        return false;
    }
}
