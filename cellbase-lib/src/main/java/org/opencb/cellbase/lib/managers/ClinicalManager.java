/*
 * Copyright 2015-2020 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.cellbase.lib.managers;

import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.*;
import org.opencb.cellbase.core.api.core.ClinicalDBAdaptor;
import org.opencb.cellbase.core.config.CellBaseConfiguration;
import org.opencb.cellbase.core.result.CellBaseDataResult;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ClinicalManager extends AbstractManager {

    public ClinicalManager(CellBaseConfiguration configuration) {
        super(configuration);
    }

    public CellBaseDataResult<Variant> search(Query query, QueryOptions queryOptions, String species, String assembly) {
        logger.debug("blahh...");
        ClinicalDBAdaptor dbAdaptor = dbAdaptorFactory.getClinicalDBAdaptor(species, assembly);
        return dbAdaptor.nativeGet(query, queryOptions);
    }

//    public List<CellBaseDataResult> getPhenotypeGeneRelations(Query query, QueryOptions queryOptions) {
//        Set<String> sourceContent = query.getAsStringList(ClinicalDBAdaptor.QueryParams.SOURCE.key()) != null
//                ? new HashSet<>(query.getAsStringList(ClinicalDBAdaptor.QueryParams.SOURCE.key())) : null;
//        List<CellBaseDataResult> cellBaseDataResultList = new ArrayList<>();
//        if (sourceContent == null || sourceContent.contains("clinvar")) {
//            cellBaseDataResultList.add(getClinvarPhenotypeGeneRelations(queryOptions));
//
//        }
//        if (sourceContent == null || sourceContent.contains("gwas")) {
//            cellBaseDataResultList.add(getGwasPhenotypeGeneRelations(queryOptions));
//        }
//
//        return cellBaseDataResultList;
//    }

    public CellBaseDataResult<String> getAlleleOriginLabels() {
        List<String> alleleOriginLabels = Arrays.stream(AlleleOrigin.values())
                .map((value) -> value.name()).collect(Collectors.toList());
        return new CellBaseDataResult<String>("allele_origin_labels", 0, Collections.emptyList(),
                alleleOriginLabels.size(), alleleOriginLabels, alleleOriginLabels.size());
    }

    public CellBaseDataResult<String> getModeInheritanceLabels() {
        List<String> modeInheritanceLabels = Arrays.stream(ModeOfInheritance.values())
                .map((value) -> value.name()).collect(Collectors.toList());
        return new CellBaseDataResult<String>("mode_inheritance_labels", 0, Collections.emptyList(),
                modeInheritanceLabels.size(), modeInheritanceLabels, modeInheritanceLabels.size());
    }

    public CellBaseDataResult<String> getClinsigLabels() {
        List<String> clinsigLabels = Arrays.stream(ClinicalSignificance.values())
                .map((value) -> value.name()).collect(Collectors.toList());
        return new CellBaseDataResult<String>("clinsig_labels", 0, Collections.emptyList(),
                clinsigLabels.size(), clinsigLabels, clinsigLabels.size());
    }

    public CellBaseDataResult<String> getConsistencyLabels() {
        List<String> consistencyLabels = Arrays.stream(ConsistencyStatus.values())
                .map((value) -> value.name()).collect(Collectors.toList());
        return  new CellBaseDataResult<String>("consistency_labels", 0, Collections.emptyList(),
                consistencyLabels.size(), consistencyLabels, consistencyLabels.size());
    }

    public CellBaseDataResult<String> getVariantTypes() {
        List<String> variantTypes = Arrays.stream(VariantType.values())
                .map((value) -> value.name()).collect(Collectors.toList());
        return new CellBaseDataResult<String>("variant_types", 0, Collections.emptyList(),
                variantTypes.size(), variantTypes, variantTypes.size());
    }

//    private CellBaseDataResult getClinvarPhenotypeGeneRelations(QueryOptions queryOptions) {
//        List<Bson> pipeline = new ArrayList<>();
//        pipeline.add(new Document("$match", new Document("clinvarSet.referenceClinVarAssertion.clinVarAccession.acc",
//                new Document("$exists", 1))));
//        pipeline.add(new Document("$unwind", "$clinvarSet.referenceClinVarAssertion.measureSet.measure"));
//        pipeline.add(new Document("$unwind", "$clinvarSet.referenceClinVarAssertion.measureSet.measure.measureRelationship"));
//        pipeline.add(new Document("$unwind", "$clinvarSet.referenceClinVarAssertion.measureSet.measure.measureRelationship.symbol"));
//        pipeline.add(new Document("$unwind", "$clinvarSet.referenceClinVarAssertion.traitSet.trait"));
//        pipeline.add(new Document("$unwind", "$clinvarSet.referenceClinVarAssertion.traitSet.trait.name"));
//        Document groupFields = new Document();
//        groupFields.put("_id", "$clinvarSet.referenceClinVarAssertion.traitSet.trait.name.elementValue.value");
//        groupFields.put("associatedGenes",
//                new Document("$addToSet",
//                        "$clinvarSet.referenceClinVarAssertion.measureSet.measure.measureRelationship.symbol.elementValue.value"));
//        pipeline.add(new Document("$group", groupFields));
//        Document fields = new Document();
//        fields.put("_id", 0);
//        fields.put("phenotype", "$_id");
//        fields.put("associatedGenes", 1);
//        pipeline.add(new Document("$project", fields));
//
//        return executeAggregation2("", pipeline, queryOptions);
//    }

//    private CellBaseDataResult getGwasPhenotypeGeneRelations(QueryOptions queryOptions) {
//        List<Bson> pipeline = new ArrayList<>();
//        // Select only GWAS documents
//        pipeline.add(new Document("$match", new Document("snpIdCurrent", new Document("$exists", 1))));
//        pipeline.add(new Document("$unwind", "$studies"));
//        pipeline.add(new Document("$unwind", "$studies.traits"));
//        Document groupFields = new Document();
//        groupFields.put("_id", "$studies.traits.diseaseTrait");
//        groupFields.put("associatedGenes", new Document("$addToSet", "$reportedGenes"));
//        pipeline.add(new Document("$group", groupFields));
//        Document fields = new Document();
//        fields.put("_id", 0);
//        fields.put("phenotype", "$_id");
//        fields.put("associatedGenes", 1);
//        pipeline.add(new Document("$project", fields));
//
//        return executeAggregation2("", pipeline, queryOptions);
//    }
//
//    public List<CellBaseDataResult<Variant>> getByVariant(List<Variant> variants, QueryOptions queryOptions) {
//        List<CellBaseDataResult<Variant>> results = new ArrayList<>(variants.size());
//        for (Variant variant : variants) {
//            results.add(getByVariant(variant, queryOptions));
//        }
//        ClinicalPhasedQueryManager phasedQueryManager = new ClinicalPhasedQueryManager();
//        if (queryOptions.get(ClinicalDBAdaptor.QueryParams.PHASE.key()) != null && (Boolean) queryOptions.get(ClinicalDBAdaptor
//        .QueryParams.PHASE.key())) {
//            results = phasedQueryManager.run(variants, results);
//
//        }
//        return results;
//    }

    public CellBaseDataResult getByRegion(Query query, QueryOptions queryOptions, String species, String assembly, String regions) {
        ClinicalDBAdaptor clinicalDBAdaptor = dbAdaptorFactory.getClinicalDBAdaptor(species, assembly);
        query.put(ClinicalDBAdaptor.QueryParams.REGION.key(), regions);
        if (hasHistogramQueryParam(queryOptions)) {
            return null;
        } else {
            CellBaseDataResult queryResult = clinicalDBAdaptor.nativeGet(query, queryOptions);
            queryResult.setId(regions);
            return queryResult;
        }
    }
}
