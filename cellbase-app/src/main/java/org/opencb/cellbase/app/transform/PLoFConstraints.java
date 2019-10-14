/*
 * Copyright 2015 OpenCB
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

package org.opencb.cellbase.app.transform;

import java.util.*;
import org.opencb.biodata.models.core.Constraint;

/**
 * Temporary holder class for the constraint scores generated by GeneParserUtils used in GeneParser.
 */
public class PLoFConstraints {

    private Map<String, Set<Constraint>> transcriptConstraints;
    private Map<String, Set<Constraint>> geneConstraints;

    public PLoFConstraints(Map<String, Set<Constraint>> transcriptConstraints, Map<String,
        Set<Constraint>> geneConstraints) {
        this.transcriptConstraints = transcriptConstraints;
        this.geneConstraints = geneConstraints;
    }

    public Set<Constraint> getTranscriptConstraintScores(String transcriptIdentifier) {
        return transcriptConstraints.get(transcriptIdentifier);
    }

    public Set<Constraint> getGeneConstraintScores(String geneIdentifier) {
        return geneConstraints.get(geneIdentifier);
    }
}