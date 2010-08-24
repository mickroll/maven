package org.apache.maven.project;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.internal.DefaultArtifactResolver;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.transfer.ArtifactNotFoundException;

/**
 * @author Benjamin Bentmann
 */
@Component( role = ArtifactResolver.class, hint = "classpath" )
public class ClasspathArtifactResolver
    extends DefaultArtifactResolver
{

    public List<ArtifactResult> resolveArtifacts( RepositorySystemSession session,
                                                  Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException
    {
        List<ArtifactResult> results = new ArrayList<ArtifactResult>();

        for ( ArtifactRequest request : requests )
        {
            ArtifactResult result = new ArtifactResult( request );
            results.add( result );

            Artifact artifact = request.getArtifact();
            if ( "maven-test".equals( artifact.getGroupId() ) )
            {
                String scope = artifact.getArtifactId().substring( "scope-".length() );

                try
                {
                    artifact = artifact.setFile( ProjectClasspathTest.getFileForClasspathResource( ProjectClasspathTest.dir
                        + "transitive-" + scope + "-dep.xml" ) );
                    result.setArtifact( artifact );
                }
                catch ( FileNotFoundException e )
                {
                    throw new IllegalStateException( "Missing test POM for " + artifact );
                }
            }
            else
            {
                result.addException( new ArtifactNotFoundException( artifact, null ) );
                throw new ArtifactResolutionException( results );
            }
        }

        return results;
    }

}
