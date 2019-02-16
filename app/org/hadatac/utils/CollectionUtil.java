package org.hadatac.utils;

import java.util.Arrays;
import java.util.List;

import org.hadatac.entity.pojo.OperationMode;

import com.typesafe.config.ConfigFactory;

public class CollectionUtil {
 
    public enum Collection {
        // data and auxiliary data 
        DATA_COLLECTION ("/sdc"), 
        DATA_ACQUISITION ("/measurement"),
        METADATA_AQUISITION ("/data_acquisitions"),
        SA_ACQUISITION ("/schema_attributes"),
        CONSOLE_STORE ("/console_store"),
        STUDIES ("/studies"),
        ANALYTES ("/analytes"),
        ANNOTATION_LOG ("/annotation_log"),
        CSV_DATASET ("/csv"),
        OPERATION_MODE ("/operation_mode"),
        NAMESPACE ("/namespace"),
        LABKEY_CREDENTIAL ("/labkey"),
        URI_GENERATOR ("/uri_generator"),
        STUDY_ACQUISITION ("/studies"),
        METADATA_DA ("/data_acquisitions"),
        ANALYTES_ACQUISITION ("/analytes"),
        SCHEMA_ATTRIBUTES ("/schema_attributes"),
        
        // triplestore
        METADATA_SPARQL ("/store/query"),
        METADATA_UPDATE ("/store/update"),
        METADATA_GRAPH ("/store/data"),

        // users 
        AUTHENTICATE_USERS ("/users"),
        AUTHENTICATE_ACCOUNTS ("/linked_account"),
        AUTHENTICATE_ROLES ("/security_role"),
        AUTHENTICATE_TOKENS ("/token_action"),
        AUTHENTICATE_PERMISSIONS ("/user_permission"),

        // permissions
        PERMISSIONS_SPARQL ("/store_users/query"),
        PERMISSIONS_UPDATE ("/store_users/update"),
        PERMISSIONS_GRAPH ("/store_users/data")
        ;
        
        private final String collectionString;

        private Collection(String collectionString) {
            this.collectionString = collectionString;
        }
        
        public String get() {
            return collectionString;
        }
    }
    
    public static String getCollectionName(String collection) {
        if (Arrays.asList(
                Collection.OPERATION_MODE.get(),
                Collection.AUTHENTICATE_ACCOUNTS.get(), 
                Collection.AUTHENTICATE_USERS.get(), 
                Collection.AUTHENTICATE_ROLES.get(),
                Collection.AUTHENTICATE_TOKENS.get(),
                Collection.AUTHENTICATE_PERMISSIONS.get(),
                Collection.PERMISSIONS_SPARQL.get(),
                Collection.PERMISSIONS_UPDATE.get(),
                Collection.PERMISSIONS_GRAPH.get()).contains(collection)) {
            return collection;
        }
        
        if (isSandboxMode()) {
            if (Arrays.asList(
                    Collection.METADATA_SPARQL.get(), 
                    Collection.METADATA_UPDATE.get(), 
                    Collection.METADATA_GRAPH.get()).contains(collection)) {
                return collection.replace("store", "store_sandbox");
            } else {
                return collection + "_sandbox";
            }
        }

        System.out.println("collection: " + collection);
        return collection;
    }
    
    public static boolean isSandboxMode() {
        List<OperationMode> modes = OperationMode.findAll();
        if (modes.size() > 0) {
            OperationMode mode = modes.get(0);
            if (mode.getOperationMode().equals(OperationMode.SANDBOX)) {
                return true;
            }
        }
        
        return false;
    }

    public static String getCollectionPath(Collection collection) {
        String collectionName = null;
        switch (collection) {
        case METADATA_SPARQL:
        case METADATA_UPDATE:
        case METADATA_GRAPH :          
            collectionName = ConfigFactory.load().getString("hadatac.solr.triplestore") + getCollectionName(collection.get());
        break;
        case AUTHENTICATE_USERS:
        case AUTHENTICATE_ACCOUNTS: 
        case AUTHENTICATE_ROLES: 
        case AUTHENTICATE_TOKENS:
        case AUTHENTICATE_PERMISSIONS: 
            collectionName = ConfigFactory.load().getString("hadatac.solr.users") + collection.get();
        break;
        case PERMISSIONS_SPARQL:
        case PERMISSIONS_UPDATE:
        case PERMISSIONS_GRAPH :       
            collectionName = ConfigFactory.load().getString("hadatac.solr.permissions") + collection.get();
        break;
        case DATA_COLLECTION:
        case DATA_ACQUISITION:
        case METADATA_AQUISITION:
        case SA_ACQUISITION :
        case CONSOLE_STORE:
        case STUDIES:
        case ANALYTES:
        case ANNOTATION_LOG:
        case OPERATION_MODE:
        case NAMESPACE:
        case CSV_DATASET:
        case LABKEY_CREDENTIAL:
        case URI_GENERATOR :
        case STUDY_ACQUISITION:
        case METADATA_DA:
        case ANALYTES_ACQUISITION:
        case SCHEMA_ATTRIBUTES:        
            collectionName = ConfigFactory.load().getString("hadatac.solr.data") + getCollectionName(collection.get());
        break;
        }
        
        return collectionName;
    }
}
