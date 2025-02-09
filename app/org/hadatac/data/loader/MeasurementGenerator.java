package org.hadatac.data.loader;

import java.time.Instant;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.hadatac.entity.pojo.ObjectAccessSpec;
import org.hadatac.entity.pojo.ObjectCollection;
import org.hadatac.entity.pojo.SOCGroup;
import org.hadatac.entity.pojo.StudyObject;
import org.hadatac.entity.pojo.StudyObjectMatching;
import org.hadatac.entity.pojo.DataAcquisitionSchema;
import org.hadatac.entity.pojo.DataAcquisitionSchemaAttribute;
import org.hadatac.entity.pojo.DataAcquisitionSchemaObject;
import org.hadatac.entity.pojo.DataFile;
import org.hadatac.entity.pojo.HADatAcThing;
import org.hadatac.entity.pojo.Measurement;
import org.hadatac.metadata.loader.URIUtils;
import org.hadatac.utils.CollectionUtil;
import org.hadatac.utils.Feedback;

public class MeasurementGenerator extends BaseGenerator {

    private ObjectAccessSpec da;
    private DataFile dataFile;

    private DataAcquisitionSchema schema = null;
    private Map<String, DataAcquisitionSchemaObject> mapSchemaObjects = new HashMap<String, DataAcquisitionSchemaObject>();
    private Map<String, DataAcquisitionSchemaObject> mapSchemaEvents = new HashMap<String, DataAcquisitionSchemaObject>();

    // ASSIGN positions for MetaDASAs
    private int posTimestamp = -1;
    private int posTimeInstant = -1;
    private int posNamedTime = -1;
    private int posId = -1;
    private int posOriginalId = -1;
    private int posEntity = -1;
    private int posUnit = -1;
    private int posInRelation = -1;
    private int posLOD = -1;
    private int posGroup = -1;
    private int posMatching = -1;

    private int totalCount = 0;

    private Map<String, Map<String, String>> possibleValues = null;
    private Map<String, String> urisByLabels = null;
    //private Map<String, Map<String, String>> mapIDStudyObjects = null;
    private Map<String,SOCGroup> groupBySocAndId = null;
    private StudyObject cellScopeObject = null;
    private ObjectCollection cellScopeSOC = null;
    private Map<String,ObjectCollection> matchingSOCs = null;

    private String dasoUnitUri = "";

    //private List<DASVirtualObject> templateList = new ArrayList<DASVirtualObject>();
    private DASOInstanceGenerator dasoiGen = null; 

    public MeasurementGenerator(DataFile dataFile, ObjectAccessSpec da, 
            DataAcquisitionSchema schema, DASOInstanceGenerator dasoiGen) {
        super(dataFile);
        this.da = da;
        this.schema = schema;
        this.dataFile = dataFile;
    	this.dasoiGen = dasoiGen;
    	
    	boolean cont = true;
        if (da.hasCellScope()) {
        	System.out.println("Measurement Generator: hasCellScope is TRUE");
        	cellScopeObject = StudyObject.find(URIUtils.replacePrefixEx(da.getCellScopeUri().get(0).trim()));
        	cellScopeSOC = ObjectCollection.find(cellScopeObject.getIsMemberOf());
        } else {
        	System.out.println("Measurement Generator: hasCellScope is FALSE");
        	if (!dasoiGen.initiateCache(da.getStudyUri())) {
        		logger.printExceptionById("DA_00001");
        		cont = false;
        	}
        	matchingSOCs = dasoiGen.getMatchingSOCs();
        }
        if (cont) {
        	System.out.println("Measurement Generator: setting STUDY URI");
    		setStudyUri(da.getStudyUri());
    		urisByLabels = DataAcquisitionSchema.findAllUrisByLabel(schema.getUri());
        }
    
    }

    @Override
    public void preprocess() throws Exception {
        System.out.println("[Parser] indexMeasurements()...");

        // ASSIGN values for tempPositionInt
        List<String> unknownHeaders = schema.defineTemporaryPositions(file.getHeaders());
        if (!unknownHeaders.isEmpty()) {
            logger.addLine(Feedback.println(Feedback.WEB, 
                    "[WARNING] Failed to match the following " 
                            + unknownHeaders.size() + " headers: " + unknownHeaders));
        }

        if (!schema.getTimestampLabel().equals("")) {
            posTimestamp = schema.tempPositionOfLabel(schema.getTimestampLabel());
            System.out.println("posTimestamp: " + posTimestamp);
        }
        if (!schema.getTimeInstantLabel().equals("")) {
            posTimeInstant = schema.tempPositionOfLabel(schema.getTimeInstantLabel());
            System.out.println("posTimeInstant: " + posTimeInstant);
        }
        if (!schema.getNamedTimeLabel().equals("")) {
            posNamedTime = schema.tempPositionOfLabel(schema.getNamedTimeLabel());
            System.out.println("posNamedTime: " + posNamedTime);
        }
        if (!schema.getIdLabel().equals("")) {
            posId = schema.tempPositionOfLabel(schema.getIdLabel());
            System.out.println("posId: " + posId);
        }
        if (!schema.getOriginalIdLabel().equals("")) {
            posOriginalId = schema.tempPositionOfLabel(schema.getOriginalIdLabel());
            System.out.println("posOriginalId: " + posOriginalId);
        }
        if (!schema.getEntityLabel().equals("")) {
            posEntity = schema.tempPositionOfLabel(schema.getEntityLabel());
            System.out.println("posEntity: " + posEntity);
        }
        if (!schema.getUnitLabel().equals("")) {
            posUnit = schema.tempPositionOfLabel(schema.getUnitLabel());
            System.out.println("posUnit: " + posUnit);
        }
        if (!schema.getInRelationToLabel().equals("")) {
            posInRelation = schema.tempPositionOfLabel(schema.getInRelationToLabel());
            System.out.println("posInRelation: " + posInRelation);
        }
        if (!schema.getLODLabel().equals("")) {
            posLOD = schema.tempPositionOfLabel(schema.getLODLabel());
            System.out.println("posLOD: " + posLOD);
        }
        if (!schema.getGroupLabel().equals("")) {
            posGroup = schema.tempPositionOfLabel(schema.getGroupLabel());
            System.out.println("posGroup: " + posGroup);
        }
        if (!schema.getMatchingLabel().equals("")) {
            posMatching = schema.tempPositionOfLabel(schema.getMatchingLabel());
            System.out.println("posMatching: " + posMatching);
        }

        // Store necessary information before hand to avoid frequent SPARQL queries
        possibleValues = DataAcquisitionSchema.findPossibleValues(da.getSchemaUri());
        urisByLabels = DataAcquisitionSchema.findAllUrisByLabel(da.getSchemaUri());
        //mapIDStudyObjects = StudyObject.findIdUriMappings(da.getStudyUri());
        dasoUnitUri = urisByLabels.get(schema.getUnitLabel());
		groupBySocAndId = new HashMap<String,SOCGroup>();

        //System.out.println("possibleValues: " + possibleValues);
        
        // Comment out row instance generation
        // Map<String, DASOInstance> rowInstances = new HashMap<String, DASOInstance>();
    }

    @Override
    public HADatAcThing createObject(Record record, int rowNumber) throws Exception {
        System.out.println("rowNumber: " + rowNumber);
        
        Map<String, Map<String,String>> objList = null;
        Map<String,String> groundObj = null;
        String socUri = "";
        String objUri = "";
        boolean doMatching = false;
        boolean doGroup = false;
        if (da.hasCellScope()) {
            socUri = cellScopeSOC.getUri();
            objUri = cellScopeObject.getUri();
        } else {
            // Objects defined by Row Scope
            String id = "";
            if (!schema.getOriginalIdLabel().equals("")) {
                id = record.getValueByColumnIndex(posOriginalId);
            } else if (!schema.getIdLabel().equals("")) {
                id = record.getValueByColumnIndex(posId);
            }
            objList = dasoiGen.generateRowInstances(id);
            groundObj = dasoiGen.retrieveGroundObject(id);
            
            // socUri and objUri for row scope is defined later under measurement processing
        }

        Iterator<DataAcquisitionSchemaAttribute> iterAttributes = schema.getAttributes().iterator();
        while (iterAttributes.hasNext()) {
            DataAcquisitionSchemaAttribute dasa = iterAttributes.next();

            if (!dasa.getPartOfSchema().equals(schema.getUri())){
                continue;
            }
            if (dasa.getLabel().equals(schema.getTimestampLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getTimeInstantLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getNamedTimeLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getIdLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getOriginalIdLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getEntityLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getUnitLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getInRelationToLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getLODLabel())) {
                continue;
            }
            if (dasa.getLabel().equals(schema.getGroupLabel())) {
            	doGroup = true;
            }
            if (dasa.getLabel().equals(schema.getMatchingLabel())) {
            	doMatching = true;
            }

            Measurement measurement = new Measurement();

            /*===================*
             *                   *
             *   SET VALUE       *
             *                   *
             *===================*/

            String originalValue = "";
            if (dasa.getTempPositionInt() < 0 || dasa.getTempPositionInt() >= record.size()) {
                continue;
            } else if (record.getValueByColumnIndex(dasa.getTempPositionInt()).isEmpty()) { 
                continue;
            } else {
                originalValue = record.getValueByColumnIndex(dasa.getTempPositionInt());
                String dasa_uri_temp = dasa.getUri();
                measurement.setOriginalValue(originalValue);
                if (possibleValues.containsKey(dasa_uri_temp)) {
                    if (possibleValues.get(dasa_uri_temp).containsKey(originalValue.toLowerCase())) {
                        measurement.setValue(possibleValues.get(dasa_uri_temp).get(originalValue.toLowerCase()));
                    } else {
                        measurement.setValue(originalValue);
                    }
                } else {
                    measurement.setValue(originalValue);
                }
            }

            /*===========================*
             *                           *
             * SET LEVEL OF DETECTION    *
             *                           *
             *===========================*/
            measurement.setLevelOfDetection("");
            if (!schema.getLODLabel().equals("") && posLOD >= 0) {
                measurement.setLevelOfDetection(record.getValueByColumnIndex(posLOD));
            }

            /*============================*
             *                            *
             *   SET TIME(STAMP)          *
             *                            *
             *============================*/

            /*
              - TimestampLabel is used for machine generated timestamp
              - TimeInstantLabel is used for timestamps told to system to be timestamp, but that are not further processed
              - Abstract times are encoded as DASA's events, and are supposed to be strings
             */
            measurement.setTimestamp(new Date(0));
            measurement.setAbstractTime("");

            if(dasa.getLabel() == schema.getTimestampLabel()) {
                // full-row regular (Epoch) timemestamp
                String sTime = record.getValueByColumnIndex(posTimestamp);
                //System.out.println("Timestamp received: " + sTime);
                int timeStamp = new BigDecimal(sTime).intValue();
                //System.out.println("Tmestamp recorded: " + Instant.ofEpochSecond(timeStamp).toString());
                measurement.setTimestamp(Instant.ofEpochSecond(timeStamp).toString());
            } else if (!schema.getTimeInstantLabel().equals("")) {
                // full-row regular (XSD) time interval
                String timeValue = record.getValueByColumnIndex(posTimeInstant);
                //System.out.println("Timestamp received: " + timeValue);
                if (timeValue != null) {
                    try {
                        measurement.setTimestamp(timeValue);
                    } catch (Exception e) {
                        //System.out.println("Setting current time!");
                        measurement.setTimestamp(new Date(0).toInstant().toString());
                    }
                }
            } else if (!schema.getNamedTimeLabel().equals("")) {
            	// full-row named time
                String timeValue = record.getValueByColumnIndex(posNamedTime);
                if (timeValue != null) {
                    measurement.setAbstractTime(timeValue);
                } else {
                    measurement.setAbstractTime("");
                }
            } else if (dasa.getEventUri() != null && !dasa.getEventUri().equals("")) {
                //DataAcquisitionSchemaEvent dase = null;
                DataAcquisitionSchemaObject dase = null;
                String daseUri = dasa.getEventUri();
                if (mapSchemaEvents.containsKey(daseUri)) {
                    dase = mapSchemaEvents.get(daseUri);
                } else {
                    dase = schema.getEvent(daseUri);
                    if (dase != null) {
                        mapSchemaEvents.put(daseUri, dase);
                    }
                }
                if (dase != null) {
                    if (!dase.getEntity().equals("")) {
                        measurement.setAbstractTime(dase.getEntity());
                    } else {
                        measurement.setAbstractTime(dase.getUri());
                    }
                }
            }

            /*===================================*
             *                                   *
             *   SET STUDY                       *
             *                                   *
             *===================================*/
            measurement.setStudyUri(da.getStudyUri());

            /*===================================*
             *                                   *
             *   SET OBJECT ID, PID, SID, ROLE   *
             *                                   *
             *===================================*/
            measurement.setObjectCollectionType("");
            measurement.setStudyObjectUri("");
            measurement.setStudyObjectTypeUri("");
            measurement.setObjectUri("");
            measurement.setPID("");
            measurement.setSID("");
            measurement.setRole("");
            measurement.setEntityUri("");

            String reference = null;
            if (da.hasCellScope()) {

                // Objects defined by Cell Scope
                if (da.getCellScopeName().get(0).equals("*")) {
                    measurement.setStudyObjectUri(cellScopeObject.getUri());
                    measurement.setStudyObjectTypeUri(cellScopeObject.getTypeUri());
                    measurement.setObjectUri(cellScopeObject.getUri());
                    measurement.setObjectCollectionType(cellScopeSOC.getTypeUri());
                    measurement.setRole(cellScopeSOC.getRoleLabel());
                    if (cellScopeObject.getOriginalId() != null) {
                    	measurement.setPID(cellScopeObject.getOriginalId());
                    }
                    //System.out.println("Measurement: ObjectURI (before replace): <" + da.getCellScopeUri().get(0).trim() + ">");
                    //System.out.println("Measurement: ObjectURI (after replace): <" + URIUtils.replacePrefixEx(da.getCellScopeUri().get(0).trim()) + ">");
                } else {
                    // TO DO: implement rest of cell scope
                }
            } else {
                // Objects defined by Row Scope
                String id = "";
                if (!schema.getOriginalIdLabel().equals("")) {
                    id = record.getValueByColumnIndex(posOriginalId);
                } else if (!schema.getIdLabel().equals("")) {
                    id = record.getValueByColumnIndex(posId);
                }

                if (!id.equals("")) {
                    reference = dasa.getObjectViewLabel();
                    if (reference != null && !reference.equals("")) {
                        if (objList.get(reference) == null) {
                            System.out.println("MeasurementGenerator: [ERROR] Processing objList for reference [" + reference + "]");
                        } else {
                            // from object list
                            objUri = objList.get(reference).get(StudyObject.STUDY_OBJECT_URI);
                            measurement.setObjectUri(objUri);
                            measurement.setObjectCollectionType(objList.get(reference).get(StudyObject.SOC_TYPE));
                            measurement.setRole(objList.get(reference).get(StudyObject.SOC_LABEL));
                            if (objList.get(reference).get(StudyObject.SOC_TYPE).equals(ObjectCollection.SAMPLE_COLLECTION)) {
                                measurement.setSID(objList.get(reference).get(StudyObject.OBJECT_ORIGINAL_ID));
                            }
                            if (objList.get(reference).get(StudyObject.OBJECT_TIME) != null && !objList.get(reference).get(StudyObject.OBJECT_TIME).equals("")) {
                                measurement.setAbstractTime(objList.get(reference).get(StudyObject.OBJECT_TIME));
                            }
                            if (objList.get(reference).get(StudyObject.SOC_URI) != null && !objList.get(reference).get(StudyObject.SOC_URI).equals("")) {
                                socUri = objList.get(reference).get(StudyObject.SOC_URI);
                            }
                            if (objList.get(reference).get(StudyObject.STUDY_OBJECT_TYPE) != null && !objList.get(reference).get(StudyObject.STUDY_OBJECT_TYPE).equals("")) {
                                String entityUri = objList.get(reference).get(StudyObject.STUDY_OBJECT_TYPE);
                                measurement.setEntityUri(entityUri);
                                if (entityUri.equals(URIUtils.replacePrefix(StudyObjectMatching.className)) && !doMatching) {
                                    String scopeObjectUri = objList.get(reference).get(StudyObject.SCOPE_OBJECT_URI);
                                	StudyObjectMatching matching = StudyObjectMatching.findByMemberUri(scopeObjectUri);
                                	if (matching != null) {
                                		measurement.setObjectUri(matching.getUri());
                                	} else {
                                		measurement.setObjectUri(objUri);
                                	}
                                	//measurement.setObjectCollectionType();
                                	//measurement.setRole();
                                	measurement.setStudyObjectUri(objList.get(reference).get(StudyObject.SCOPE_OBJECT_URI));
                                	measurement.setStudyObjectTypeUri(groundObj.get(StudyObject.STUDY_OBJECT_TYPE));
                                	//measurement.setPID();
                                }
                            }

                            // from ground object
                            if (groundObj == null || groundObj.get(StudyObject.STUDY_OBJECT_URI) == null || groundObj.get(StudyObject.STUDY_OBJECT_URI).equals("")) {
                                System.out.println("MeasurementGenerator: [ERROR] Could not retrieve Ground Object for reference [" + reference + "]");
                            } else {
                                measurement.setStudyObjectUri(groundObj.get(StudyObject.STUDY_OBJECT_URI));
                                measurement.setStudyObjectTypeUri(groundObj.get(StudyObject.STUDY_OBJECT_TYPE));
                                measurement.setPID(groundObj.get(StudyObject.SUBJECT_ID));
                            }
                        }
                        //System.out.println("[MeasurementGenerator] For Id=[" + id + "] and reference=[" + reference + "] it was assigned Obj URI=[" + measurement.getObjectUri() + "]");
                    } else {
                        System.out.println("MeasurementGenerator: [ERROR]: could not find DASA reference for ID=[" + id + "]");
                    }
                    
                }
            }

            /*=====================================*
             *                                     *
             *   SET GROUP                         *
             *                                     *
             *=====================================*/
            
            if (doGroup && !schema.getGroupLabel().equals("") && posGroup >= 0 && !socUri.equals("") && !objUri.equals("")) {
                // group value exists
                String groupValue = record.getValueByColumnIndex(posGroup);
                if (groupValue != null) {

                	// verify if SOC has a group with id=groupValue. If not, create new group inside SOC
                	SOCGroup grp = null;
                	String key = socUri + "=" + groupValue;
                	if (groupBySocAndId.containsKey(key)) {
                		grp = groupBySocAndId.get(key);
                	} else {
                		grp = SOCGroup.findBySOCUriAndId(socUri, groupValue);
                		if (grp == null) {
                			grp = new SOCGroup(socUri, groupValue);
                			grp.saveToTripleStore();
                			groupBySocAndId.put(key, grp);
                		}
                	}              	
                	// add object URI to group
                	grp.addMemberUri(objUri);
                	grp.saveMemberToTripleStore(objUri);
                
                }
                doGroup = false;
            }

        	/*=====================================*
             *                                     *
             *   SET MATCHING                      *
             *                                     *
             *=====================================*/
            
            if (doMatching && !schema.getMatchingLabel().equals("") && posMatching >= 0) {
                String scopeObjectUri = objList.get(reference).get(StudyObject.SCOPE_OBJECT_URI);
                String scopeObjectSOCUri = objList.get(reference).get(StudyObject.SCOPE_OBJECT_SOC_URI);
            	String matchingValue = record.getValueByColumnIndex(posMatching);
                if (matchingSOCs.containsKey(scopeObjectSOCUri) && matchingValue != null && !matchingValue.isEmpty()) {
                	ObjectCollection matchingSOC = matchingSOCs.get(scopeObjectSOCUri);
                	StudyObjectMatching matching = StudyObjectMatching.find(matchingSOC.getUri(), matchingValue);
                	if (matching == null) {
                		matching = new StudyObjectMatching(matchingSOC.getUri(), matchingValue);
                		matching.addMemberUri(scopeObjectUri);
                		matching.saveToTripleStore();
                	} else {
                		matching.addMemberUri(scopeObjectUri);
                		matching.saveLastMemberTripleStore();
                	}
                    measurement.setObjectUri(matching.getUri());
                    measurement.setObjectCollectionType(matchingSOC.getTypeUri());
                }
                doMatching = false;
            }

            /*=====================================*
             *                                     *
             *   SET URI, OWNER AND DA URI         *
             *                                     *
             *=====================================*/

            measurement.setUri(URIUtils.replacePrefixEx(measurement.getStudyUri()) + "/" + 
                    URIUtils.replaceNameSpaceEx(da.getUri()).split(":")[1] + "/" +
                    dasa.getLabel() + "/" + 
                    dataFile.getFileName() + "-" + totalCount++);
            measurement.setOwnerUri(da.getOwnerUri());
            measurement.setAcquisitionUri(da.getUri());

            /*======================================*
             *                                      *
             *   SET ENTITY AND CHARACTERISTIC URI  *              *
             *                                      *
             *======================================*/
            measurement.setDasoUri(dasa.getObjectUri());
            measurement.setDasaUri(dasa.getUri());

            DataAcquisitionSchemaObject daso = null;
            String dasoUri = dasa.getObjectUri();
            if (mapSchemaObjects.containsKey(dasoUri)) {
                daso = mapSchemaObjects.get(dasoUri);
            } else {
                daso = schema.getObject(dasoUri);
                mapSchemaObjects.put(dasoUri, daso);
            }

            if (measurement.getEntityUri().equals("")) {
                if (null != daso) {
                    if (daso.getTempPositionInt() > 0) {
                        // values of daso exist in the columns
                        String dasoValue = record.getValueByColumnIndex(daso.getTempPositionInt());
                        if (possibleValues.containsKey(dasa.getObjectUri())) {
                            if (possibleValues.get(dasa.getObjectUri()).containsKey(dasoValue.toLowerCase())) {
                                measurement.setEntityUri(possibleValues.get(dasa.getObjectUri()).get(dasoValue.toLowerCase()));
                            } else {
                                measurement.setEntityUri(dasoValue);
                            }
                        } else {
                            measurement.setEntityUri(dasoValue);
                        }
                    } else {
                        measurement.setEntityUri(daso.getEntity());
                    }
                } else {
                    measurement.setEntityUri(dasa.getObjectUri());
                }
            }

            measurement.setCharacteristicUris(Arrays.asList(dasa.getReversedAttributeString()));

            /*======================================*
             *                                      *
             *   SET IN RELATION TO URI             *
             *                                      *
             *======================================*/
            measurement.setInRelationToUri("");

            DataAcquisitionSchemaObject inRelationToDaso = null;
            String inRelationToUri = dasa.getInRelationToUri(URIUtils.replacePrefixEx("sio:inRelationTo"));
            if (mapSchemaObjects.containsKey(inRelationToUri)) {
                inRelationToDaso = mapSchemaObjects.get(inRelationToUri);
            } else {
                inRelationToDaso = schema.getObject(inRelationToUri);
                mapSchemaObjects.put(inRelationToUri, inRelationToDaso);
            }

            if (null != inRelationToDaso) {
                if (inRelationToDaso.getTempPositionInt() > 0) {
                    String inRelationToDasoValue = record.getValueByColumnIndex(inRelationToDaso.getTempPositionInt());
                    if (possibleValues.containsKey(inRelationToUri)) {
                        if (possibleValues.get(inRelationToUri).containsKey(inRelationToDasoValue.toLowerCase())) {
                            measurement.setInRelationToUri(possibleValues.get(inRelationToUri).get(inRelationToDasoValue.toLowerCase()));
                        }
                    }
                } else {
                    // Assign the entity of inRelationToDaso to inRelationToUri
                    measurement.setInRelationToUri(inRelationToDaso.getEntity());
                }
            }

            /*=============================*
             *                             *
             *   SET UNIT                  *
             *                             *
             *=============================*/

            if (!schema.getUnitLabel().equals("") && posUnit >= 0) {
                // unit exists in the columns
                String unitValue = record.getValueByColumnIndex(posUnit);
                if (unitValue != null) {
                    if (possibleValues.containsKey(dasoUnitUri)) {
                        if (possibleValues.get(dasoUnitUri).containsKey(unitValue.toLowerCase())) {
                            measurement.setUnitUri(possibleValues.get(dasoUnitUri).get(unitValue.toLowerCase()));
                        } else {
                            measurement.setUnitUri(unitValue);
                        }
                    } else {
                        measurement.setUnitUri(unitValue);
                    }
                }
            } else {
                measurement.setUnitUri("");
            }

            if (measurement.getUnitUri().equals("") && !dasa.getUnit().equals("")) {
                // Assign units from the Unit column of SDD
                measurement.setUnitUri(dasa.getUnit());
            }

            /*=================================*
             *                                 *
             *   SET DATASET                   *
             *                                 *
             *=================================*/
            measurement.setDatasetUri(dataFile.getDatasetUri());

            objects.add(measurement);
        }

        return null;
    }

    @Override
    public boolean commitObjectsToSolr(List<HADatAcThing> objects) throws Exception {
        SolrClient solr = new HttpSolrClient.Builder(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.DATA_ACQUISITION)).build();

        int count = 0;
        int batchSize = 10000;

        for (HADatAcThing measurement : objects) {
            try {
                solr.addBean(measurement);
            } catch (IOException | SolrServerException e) {
                System.out.println("[ERROR] SolrClient.addBean - e.Message: " + e.getMessage());
            }

            // INTERMEDIARY COMMIT
            if((++count) % batchSize == 0) {
                commitToSolr(solr, batchSize);
            }
        }

        // FINAL COMMIT
        commitToSolr(solr, count % batchSize);

        da.addNumberDataPoints(totalCount);
        da.saveToSolr();

        logger.addLine(Feedback.println(Feedback.WEB, String.format(
                "[OK] %d object(s) have been committed to solr", count)));

        return true;
    }

    private void commitToSolr(SolrClient solr, int batch_size) throws Exception {
        try {
            System.out.println("solr.commit()...");
            solr.commit();
            System.out.println(String.format("[OK] Committed %s measurements!", batch_size));
            logger.addLine(Feedback.println(Feedback.WEB, String.format("[OK] Committed %s measurements!", batch_size)));
        } catch (IOException | SolrServerException e) {
            System.out.println("[ERROR] SolrClient.commit - e.Message: " + e.getMessage());
            try {
                solr.close();
            } catch (IOException e1) {
                System.out.println("[ERROR] SolrClient.close - e.Message: " + e1.getMessage());
            }

            throw new Exception("Fail to commit to solr");
        }
    }

    @Override
    public String getTableName() {
        return "";
    }
}
