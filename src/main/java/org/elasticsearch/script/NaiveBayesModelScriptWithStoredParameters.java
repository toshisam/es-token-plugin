package org.elasticsearch.script;

import org.apache.spark.mllib.classification.NaiveBayesModel;
import org.apache.spark.mllib.linalg.Vectors;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugin.TokenPlugin;
import org.elasticsearch.search.lookup.IndexField;
import org.elasticsearch.search.lookup.IndexFieldTerm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * Script for predicting class with a Naive Bayes model
 */
public class NaiveBayesModelScriptWithStoredParameters extends AbstractSearchScript {

    NaiveBayesModel model = null;
    String field = null;
    double[] tfs = null;
    ArrayList features = null;

    final static public String SCRIPT_NAME = "naive_bayes_model_stored_parameters";

    /**
     * Factory that is registered in
     * {@link TokenPlugin#onModule(ScriptModule)}
     * method when the plugin is loaded.
     */
    public static class Factory implements NativeScriptFactory {

        final Node node;

        @Inject
        public Factory(Node node) {
            // Node is not fully initialized here
            // All we can do is save a reference to it for future use
            this.node = node;
        }

        /**
         * This method is called for every search on every shard.
         *
         * @param params list of script parameters passed with the query
         * @return new native script
         */
        @Override
        public ExecutableScript newScript(@Nullable Map<String, Object> params) throws ScriptException {
            return new NaiveBayesModelScriptWithStoredParameters(params, node.client());
        }
    }

    /**
     * @param params terms that a used for classification and model parameters. Initialize
     *               naive bayes model here.
     * @throws ScriptException
     */
    private NaiveBayesModelScriptWithStoredParameters(Map<String, Object> params, Client client) throws ScriptException {
        // get the terms

        String index = (String) params.get("index");
        if (index == null) {
            throw new ScriptException("cannot initialize " + SCRIPT_NAME + ": parameter \"index\" missing");
        }
        String type = (String) params.get("type");
        if (index == null) {
            throw new ScriptException("cannot initialize " + SCRIPT_NAME + ": parameter \"type\" missing");
        }
        String id = (String) params.get("id");
        if (index == null) {
            throw new ScriptException("cannot initialize " + SCRIPT_NAME + ": parameter \"id\" missing");
        }

        // get the parameters from somewhere else
        GetResponse getResponse = client.prepareGet(index, type, id).get();
        if (getResponse.isExists() == false) {
            throw new ScriptException("cannot initialize " + SCRIPT_NAME + ": document " + index + "/" + type + "/" + id);
        }

        // get the field
        field = (String) params.get("field");
        ArrayList piAsArrayList = (ArrayList) getResponse.getSource().get("pi");
        ArrayList labelsAsArrayList = (ArrayList) getResponse.getSource().get("labels");
        ArrayList thetasAsArrayList = (ArrayList) getResponse.getSource().get("thetas");
        features = (ArrayList) getResponse.getSource().get("features");
        if (field == null || features == null || piAsArrayList == null || labelsAsArrayList == null || thetasAsArrayList == null) {
            throw new ScriptException("cannot initialize " + SCRIPT_NAME + ": one of the following parameters missing: field, features, pi, thetas, labels");
        }
        tfs = new double[features.size()];
        double[] pi = new double[piAsArrayList.size()];
        for (int i = 0; i < piAsArrayList.size(); i++) {
            pi[i] = ((Number) piAsArrayList.get(i)).doubleValue();
        }
        double[] labels = new double[labelsAsArrayList.size()];
        for (int i = 0; i < labelsAsArrayList.size(); i++) {
            labels[i] = ((Number) labelsAsArrayList.get(i)).doubleValue();
        }
        double thetas[][] = new double[labels.length][features.size()];
        for (int i = 0; i < thetasAsArrayList.size(); i++) {
            ArrayList thetaRow = (ArrayList) thetasAsArrayList.get(i);
            for (int j = 0; j < thetaRow.size(); j++) {
                thetas[i][j] = ((Number) thetaRow.get(j)).doubleValue();
            }
        }
        model = new NaiveBayesModel(labels, pi, thetas);
    }

    @Override
    public Object run() {
        try {
            /** here be the vectorizer **/
            IndexField indexField = this.indexLookup().get(field);
            for (int i = 0; i < features.size(); i++) {
                IndexFieldTerm indexTermField = indexField.get(features.get(i));
                tfs[i] = indexTermField.tf();
            }
            /** until here **/
            return model.predict(Vectors.dense(tfs));
        } catch (IOException ex) {
            throw new ScriptException("Model prediction failed: ", ex);
        }
    }
}