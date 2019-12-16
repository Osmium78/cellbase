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

package org.opencb.cellbase.server.rest.regulatory;

import io.swagger.annotations.Api;
import org.opencb.cellbase.core.exception.CellbaseException;
import org.opencb.cellbase.server.exception.SpeciesException;
import org.opencb.cellbase.server.exception.VersionException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;

@Path("/{version}/{species}/regulatory/mirna_gene")
@Produces("text/plain")
@Api(value = "miRNA gene", description = "miRNA RESTful Web Services API", hidden = true)
public class MiRnaGeneWSServer extends RegulatoryWSServer {


    public MiRnaGeneWSServer(@PathParam("version") String version, @PathParam("species") String species, @Context UriInfo uriInfo,
                             @Context HttpServletRequest hsr) throws VersionException, SpeciesException, IOException, CellbaseException {
        super(version, species, uriInfo, hsr);
    }

    @GET
    public Response defaultMethod() {
        return help();
    }

    @GET
    @Path("/help")
    public Response help() {
        StringBuilder sb = new StringBuilder();
        sb.append("Input:\n");
        sb.append("all id formats are accepted.\n\n\n");
        sb.append("Resources:\n");
        sb.append("- info: Get information about a miRNA gene: name, accession, status and sequence.\n");
        sb.append(" Output columns: miRBase accession, miRBase ID, status, sequence, source.\n\n");
        sb.append("- target: Get target sites for this miRNA.\n");
        sb.append(" Output columns: miRBase ID, gene target name, chromosome, start, end, strand, pubmed ID, source.\n\n");
        sb.append("- disease: Get all diseases related with this miRNA.\n");
        sb.append(" Output columns: miRBase ID, disease name, pubmed ID, description.\n\n\n");
        sb.append("Documentation:\n");
        sb.append("http://docs.bioinfo.cipf.es/projects/cellbase/wiki/Regulatory_rest_ws_api#MicroRNA-gene");

        return createOkResponse(sb.toString());
    }
}