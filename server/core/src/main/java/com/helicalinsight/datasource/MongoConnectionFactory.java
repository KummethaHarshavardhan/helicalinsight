package com.helicalinsight.datasource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;

import com.helicalinsight.admin.model.HIHcrConnections;
import com.helicalinsight.admin.model.HIResource;
import com.helicalinsight.admin.model.HIResourceHCR;
import com.helicalinsight.admin.service.HIResourceServiceDB;
import com.helicalinsight.datasource.service.EFWDConnectionService;
import com.helicalinsight.efw.framework.utils.ApplicationContextAccessor;
import com.helicalinsight.efw.utility.JsonUtils;


/**
 * MongoConnectionFactory extends {@link DatabaseConnectionFactory}
 * Manages MongoDB database connections and provides methods for connecting to various data sources.
 * Responsible for creating connections to MongoDB databases.
 */
@SuppressWarnings("unused")
public class MongoConnectionFactory extends DatabaseConnectionFactory {

    private final HIResourceServiceDB serviceDB =
            ApplicationContextAccessor.getBean(HIResourceServiceDB.class);

    private EFWDConnectionService efwdService =
            ApplicationContextAccessor.getBean(EFWDConnectionService.class);


    /**
     * Retrieves a database connection based on the provided
     * data source type and JSON information.
     *
     * @param type     Type of data source.
     * @param jsonInfo JSON information containing connection details.
     * @return A DriverConnection object representing the connection.
     */
    @Override
    public DriverConnection getConnection(
            String type,
            String jsonInfo) {

        String driverClassName = null;

        JsonObject formJson =
                new Gson().fromJson(
                        jsonInfo,
                        JsonObject.class
                );


        /*
         * Temporary connection.
         */
        if (formJson.has("isTemp")) {

            return super.getConnectionFromTemp(
                    type,
                    jsonInfo
            );
        }


        /*
         * Load EFWD information when the resource
         * contains dir and uuid.
         */
        if (!formJson.has("efwd")
                && formJson.has("dir")
                && formJson.has("uuid")) {

            HIResource hiResource =
                    serviceDB.getResourceByUrl(
                            formJson.get("dir").getAsString()
                                    + "/"
                                    + formJson.get("uuid").getAsString()
                    );

            HIResourceHCR hiResourceHCR =
                    hiResource.getHiResourceHCR();

            List<HIHcrConnections> hcrConnections =
                    efwdService.fetchAllHcrConnectionsByResourceId(
                            hiResource.getResourceId()
                    );

            JsonObject efwd =
                    com.helicalinsight.datasource.managed.JsonUtils
                            .prepareEfwJsonByHcr(
                                    hcrConnections
                            );

            formJson.add(
                    "efwd",
                    efwd.getAsJsonObject("efwd")
            );

            jsonInfo =
                    formJson.toString();
        }


        /*
         * Process driver information for non-global JDBC
         * connections.
         */
        if (!type.equalsIgnoreCase(GlobalJdbcType.TYPE)) {

            JsonObject connectionDetails = null;


            if (formJson.has("connectionJson")) {

                connectionDetails =
                        formJson.getAsJsonObject(
                                "connectionJson"
                        );

            } else {

                connectionDetails =
                        DataSourceUtils.getConnectionJson(
                                formJson
                        );
            }


            /*
             * Check all supported property names
             * used for identifying the driver.
             */
            if (connectionDetails.has("driverClassName")) {

                driverClassName =
                        connectionDetails
                                .get("driverClassName")
                                .getAsString();
            }


            if (connectionDetails.has("Driver")) {

                driverClassName =
                        connectionDetails
                                .get("Driver")
                                .getAsString();
            }


            if (connectionDetails.has("driverName")) {

                driverClassName =
                        connectionDetails
                                .get("driverName")
                                .getAsString();
            }


            /*
             * MongoDB driver support.
             *
             * Existing driver:
             * mongodb.jdbc.MongoDriver
             *
             * Helical MongoDB driver:
             * com.helical.mongodb.MongoJdbcDriver
             *
             * Both are supported here.
             */
            if (isMongoDriver(driverClassName)) {

                DriverConnection driverConnection =
                        new DriverConnection();

                /*
                 * MongoDB is handled by the Mongo-specific
                 * connection layer / Drill loader.
                 */
                driverConnection.setConnection(null);

                driverConnection.setDriverClass(
                        driverClassName
                );

                return driverConnection;
            }


            /*
             * Existing Helical middleware driver logic.
             */
            if (driverClassName != null
                    && driverClassName.startsWith(
                            JsonUtils.getHiMiddleWareName()
                    )) {

                formJson.addProperty(
                        "id",
                        "-1"
                );

                jsonInfo =
                        formJson.toString();
            }
        }


        /*
         * Preserve existing DatabaseConnectionFactory
         * behaviour for all other data sources.
         */
        return super.getConnection(
                type,
                jsonInfo
        );
    }


    /**
     * Checks whether the supplied driver name
     * represents a supported MongoDB driver.
     *
     * @param driverClassName driver class name
     * @return true when MongoDB driver is detected
     */
    private boolean isMongoDriver(
            String driverClassName) {

        if (driverClassName == null) {

            return false;
        }

        return "mongodb.jdbc.MongoDriver"
                .equalsIgnoreCase(driverClassName)

                || "com.helical.mongodb.MongoJdbcDriver"
                .equalsIgnoreCase(driverClassName);
    }


    @Override
    public boolean isThreadSafeToCache() {

        return true;
    }
}