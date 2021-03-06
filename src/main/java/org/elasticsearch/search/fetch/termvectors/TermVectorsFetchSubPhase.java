/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.fetch.termvectors;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.termvectors.TermVectorsRequest;
import org.elasticsearch.action.termvectors.TermVectorsResponse;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.termvectors.TermVectorsService;
import org.elasticsearch.script.SharedMethods;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.search.internal.InternalSearchHitField;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;


public class TermVectorsFetchSubPhase implements FetchSubPhase {

    public static final String NAME = "termvectors";

    @Override
    public void hitExecute(SearchContext context, HitContext hitContext) {
        TermVectorsFetchBuilder fetchSubPhaseBuilder = (TermVectorsFetchBuilder)context.getSearchExt(NAME);
        if (fetchSubPhaseBuilder == null) {
            return;
        }
        if (hitContext.hit().fieldsOrNull() == null) {
            hitContext.hit().fields(new HashMap<String, SearchHitField>());
        }
        SearchHitField hitField = hitContext.hit().fields().get(NAME);
        if (hitField == null) {
            hitField = new InternalSearchHitField(NAME, new ArrayList<>(1));
            hitContext.hit().fields().put(NAME, hitField);
        }
        TermVectorsRequest request = fetchSubPhaseBuilder.getRequest();
        request.id(hitContext.hit().id());
        request.type(hitContext.hit().type());
        request.index(context.indexShard().shardId().getIndexName());
        TermVectorsResponse termVector = TermVectorsService.getTermVectors(context.indexShard(), request);
        XContentBuilder builder;
        try {
            builder = jsonBuilder();
            builder.startObject();
            termVector.toXContent(builder, ToXContent.EMPTY_PARAMS);
            builder.endObject();
        } catch (IOException e) {
            throw new ElasticsearchException("could not build term vector respoonse", e);
        }


        try {
            Map<String, Object> termVectorAsMap = SharedMethods.getSourceAsMap(builder.string());
            hitField.values().add(termVectorAsMap.get("term_vectors"));
        } catch (IOException e) {
            throw new ElasticsearchException("retrieving term vectors failed", e);
        }
    }
}