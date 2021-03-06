package com.github.cleverage.elasticsearch;

import com.github.cleverage.elasticsearch.annotations.IndexMapping;
import com.github.cleverage.elasticsearch.annotations.IndexType;
import org.reflections.Reflections;
import play.Application;
import play.Configuration;
import play.Logger;
import play.libs.ReflectionsCache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * User: nboire
 *
 * example :
 * elasticsearch.local=false
 * elasticsearch.client="192.168.0.46:9300"
 * elasticsearch.cluster.name=myCluster
 * elasticsearch.index.name=play2-elasticsearch
 * elasticsearch.index.settings="{ analysis: { analyzer: { my_analyzer: { type: \"custom\", tokenizer: \"standard\" } } } }"
 * elasticsearch.index.clazzs="indexing.*"
 * elasticsearch.index.show_request=true
 */
public class IndexConfig {

    /**
     *  elasticsearch.local= true / false
     *  Mode local or network
     */
    public static Boolean local = false;

    /**
     * elasticsearch.local.config = configuration file load on local mode.
     * eg : conf/elasticsearch.yml
     */
    public static String localConfig = null;

    /**
     *  elasticsearch.client = list of client separate by commas ex : 192.168.0.1:9300,192.168.0.2:9300
     */
    public static String client = null;

    /**
     * elasticsearch.cluster.name = name of the elasticsearch cluster
     */
    public static String clusterName = null;

    /**
     * Debug mode for log search request and response
     */
    public static Boolean showRequest = false;

    /**
     * The name of the index
     */
    public static String indexName = null;

    /**
     * Custom settings to apply when creating the index. ex: "{ analysis: { analyzer: { my_analyzer: { type : "custom", tokenizer: "standard" } } } }" 
     */
    public static String indexSettings = null;

    /**
     * list of class extends "Index" ex: myPackage.myClass,myPackage2.*
     */
    public static String indexClazzs = null;

    /**
     * List of IndexType and IndexMapping associate
     */
    public static Map<String, String> indexTypes = new HashMap<String, String>();

    /**
     * Drop the index on application shutdown
     * Should probably be used only in tests
     */
    public static boolean dropOnShutdown = false;

    /**
     * Play application
     */
    public static Application application;

    public IndexConfig(Application app) {
        this.application = app;
        this.client = app.configuration().getString("elasticsearch.client");
        this.local = app.configuration().getBoolean("elasticsearch.local");
        this.localConfig = app.configuration().getString("elasticsearch.config.resource");
        this.clusterName = app.configuration().getString("elasticsearch.cluster.name");

        this.indexName = app.configuration().getString("elasticsearch.index.name");
        this.indexSettings = app.configuration().getString("elasticsearch.index.settings");
        this.indexClazzs = app.configuration().getString("elasticsearch.index.clazzs");

        this.showRequest = app.configuration().getBoolean("elasticsearch.index.show_request", false);

        this.dropOnShutdown = app.configuration().getBoolean("elasticsearch.index.dropOnShutdown", false);

        loadMappingFromAnnotations();
        loadMappingFromConfig();
    }


    /**
     * Load classes with @IndexType and initialize mapping if present on the @IndexMapping
     */
    private void loadMappingFromAnnotations() {

        Set<String> classes = getClazzs();

        for (String aClass : classes) {
            Class<?> klass = null;
            try {
                // Loading class and annotation for set mapping if is present
                Logger.debug("ElasticSearch : Registering class " + aClass);

                klass = Class.forName(aClass, true, application.classloader());
                Object o = klass.newInstance();

                String indexType = getIndexType(o);
                String indexMapping = getIndexMapping(o);

                if (indexType != null) {
                    indexTypes.put(indexType, indexMapping);
                }
            } catch (Throwable e) {
                Logger.error(e.getMessage());
            }
        }
    }

    /**
     * Load additional mappings from config entry "elasticsearch.index.mapping"
     */
    private void loadMappingFromConfig() {
        Configuration mappingConfig = application.configuration().getConfig("elasticsearch.index.mappings");
        if (mappingConfig != null) {
            Map<String, Object> mappings = mappingConfig.asMap();
            for (String indexType : mappings.keySet()) {
                if (mappings.get(indexType) instanceof String) {
                    indexTypes.put(indexType, (String)mappings.get(indexType));
                } else {
                    Logger.warn("Incorrect value in elasticsearch.index.mappings");
                }
            }
        }
    }

    private String getIndexType(Object instance) {
        IndexType indexTypeAnnotation = instance.getClass().getAnnotation(IndexType.class);
        if (indexTypeAnnotation == null) {
            return null;
        }
        return indexTypeAnnotation.name();
    }

    private String getIndexMapping(Object instance) {
        IndexMapping indexMapping = instance.getClass().getAnnotation(IndexMapping.class);
        if (indexMapping == null) {
            return null;
        }
        return indexMapping.value();
    }

    private Set<String> getClazzs() {

        Set<String> classes = new HashSet<String>();

        if (indexClazzs != null) {
            String[] toLoad = indexClazzs.split(",");
            for (String load : toLoad) {
                load = load.trim();
                if (load.endsWith(".*")) {
                        Reflections reflections = ReflectionsCache.getReflections(application.classloader(), load.substring(0, load.length() - 2));
                        for(Class c :reflections.getTypesAnnotatedWith(IndexType.class)){
                        classes.add(c.getName());
                    }
                } else {
                    classes.add(load);
                }
            }
        }
        return classes;
    }
}
