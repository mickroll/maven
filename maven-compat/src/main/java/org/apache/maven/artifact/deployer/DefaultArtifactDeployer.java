package org.apache.maven.artifact.deployer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.MetadataBridge;
import org.apache.maven.artifact.repository.metadata.SnapshotArtifactRepositoryMetadata;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeployResult;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.metadata.MergeableMetadata;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.artifact.SubArtifact;

@Component( role = ArtifactDeployer.class, instantiationStrategy = "per-lookup" )
public class DefaultArtifactDeployer
    extends AbstractLogEnabled
    implements ArtifactDeployer
{

    @Requirement
    private RepositorySystem repoSystem;

    @Requirement
    private LegacySupport legacySupport;

    private Map<Object, MergeableMetadata> snapshots = new ConcurrentHashMap<Object, MergeableMetadata>();

    /**
     * @deprecated we want to use the artifact method only, and ensure artifact.file is set
     *             correctly.
     */
    @Deprecated
    public void deploy( String basedir, String finalName, Artifact artifact, ArtifactRepository deploymentRepository,
                        ArtifactRepository localRepository )
        throws ArtifactDeploymentException
    {
        String extension = artifact.getArtifactHandler().getExtension();
        File source = new File( basedir, finalName + "." + extension );
        deploy( source, artifact, deploymentRepository, localRepository );
    }

    public void deploy( File source, Artifact artifact, ArtifactRepository deploymentRepository,
                        ArtifactRepository localRepository )
        throws ArtifactDeploymentException
    {
        DefaultRepositorySystemSession session =
            new DefaultRepositorySystemSession( legacySupport.getRepositorySession() );
        LocalRepository localRepo = new LocalRepository( localRepository.getBasedir() );
        session.setLocalRepositoryManager( repoSystem.newLocalRepositoryManager( localRepo ) );

        DeployRequest request = new DeployRequest();

        org.sonatype.aether.artifact.Artifact mainArtifact = RepositoryUtils.toArtifact( artifact );
        mainArtifact = mainArtifact.setFile( source );
        request.addArtifact( mainArtifact );

        String snapshotKey = null;
        if ( artifact.isSnapshot() )
        {
            snapshotKey = artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getBaseVersion();
            request.addMetadata( snapshots.get( snapshotKey ) );
        }

        for ( ArtifactMetadata metadata : artifact.getMetadataList() )
        {
            if ( metadata instanceof ProjectArtifactMetadata )
            {
                org.sonatype.aether.artifact.Artifact pomArtifact = new SubArtifact( mainArtifact, "", "pom" );
                pomArtifact = pomArtifact.setFile( ( (ProjectArtifactMetadata) metadata ).getFile() );
                request.addArtifact( pomArtifact );
            }
            else if ( metadata instanceof SnapshotArtifactRepositoryMetadata
                || metadata instanceof ArtifactRepositoryMetadata )
            {
                // eaten, handled by repo system
            }
            else
            {
                request.addMetadata( new MetadataBridge( metadata ) );
            }
        }

        RemoteRepository remoteRepo = RepositoryUtils.toRepo( deploymentRepository );
        /*
         * NOTE: This provides backward-compat with maven-deploy-plugin:2.4 which bypasses the repository factory when
         * using an alternative deployment location.
         */
        if ( deploymentRepository instanceof DefaultArtifactRepository
            && deploymentRepository.getAuthentication() == null )
        {
            remoteRepo.setAuthentication( session.getAuthenticationSelector().getAuthentication( remoteRepo ) );
            remoteRepo.setProxy( session.getProxySelector().getProxy( remoteRepo ) );
        }
        request.setRepository( remoteRepo );

        DeployResult result;
        try
        {
            result = repoSystem.deploy( session, request );
        }
        catch ( DeploymentException e )
        {
            throw new ArtifactDeploymentException( e.getMessage(), e );
        }

        if ( snapshotKey != null )
        {
            for ( Object metadata : result.getMetadata() )
            {
                if ( metadata.getClass().getName().endsWith( ".internal.RemoteSnapshotMetadata" ) )
                {
                    snapshots.put( snapshotKey, (MergeableMetadata) metadata );
                }
            }
        }

        artifact.setResolvedVersion( result.getArtifacts().iterator().next().getVersion() );
    }

}
