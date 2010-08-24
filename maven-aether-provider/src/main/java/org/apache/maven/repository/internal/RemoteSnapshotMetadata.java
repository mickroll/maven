package org.apache.maven.repository.internal;

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

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.sonatype.aether.artifact.Artifact;

/**
 * @author Benjamin Bentmann
 */
final class RemoteSnapshotMetadata
    extends MavenMetadata
{

    private static final String SNAPSHOT = "SNAPSHOT";

    private final Collection<Artifact> artifacts = new ArrayList<Artifact>();

    private final Map<String, SnapshotVersion> versions = new LinkedHashMap<String, SnapshotVersion>();

    public RemoteSnapshotMetadata( Artifact artifact )
    {
        super( createMetadata( artifact ), null );
    }

    private RemoteSnapshotMetadata( Metadata metadata, File file )
    {
        super( metadata, file );
    }

    private static Metadata createMetadata( Artifact artifact )
    {
        Metadata metadata = new Metadata();
        metadata.setModelVersion( "1.1.0" );
        metadata.setGroupId( artifact.getGroupId() );
        metadata.setArtifactId( artifact.getArtifactId() );
        metadata.setVersion( artifact.getBaseVersion() );

        return metadata;
    }

    public void bind( Artifact artifact )
    {
        artifacts.add( artifact );
    }

    public MavenMetadata setFile( File file )
    {
        return new RemoteSnapshotMetadata( metadata, file );
    }

    public Object getKey()
    {
        return getGroupId() + ':' + getArtifactId() + ':' + getVersion();
    }

    public static Object getKey( Artifact artifact )
    {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getBaseVersion();
    }

    public String getExpandedVersion( Artifact artifact )
    {
        return versions.get( artifact.getClassifier() ).getVersion();
    }

    @Override
    protected void merge( Metadata recessive )
    {
        Snapshot snapshot;
        String lastUpdated = "";

        if ( metadata.getVersioning() == null )
        {
            DateFormat utcDateFormatter = new SimpleDateFormat( "yyyyMMdd.HHmmss" );
            utcDateFormatter.setTimeZone( TimeZone.getTimeZone( "UTC" ) );

            snapshot = new Snapshot();
            snapshot.setBuildNumber( getBuildNumber( recessive ) + 1 );
            snapshot.setTimestamp( utcDateFormatter.format( new Date() ) );

            Versioning versioning = new Versioning();
            versioning.setSnapshot( snapshot );
            versioning.setLastUpdated( snapshot.getTimestamp().replace( ".", "" ) );
            lastUpdated = versioning.getLastUpdated();

            metadata.setVersioning( versioning );
        }
        else
        {
            snapshot = metadata.getVersioning().getSnapshot();
            lastUpdated = metadata.getVersioning().getLastUpdated();
        }

        for ( Artifact artifact : artifacts )
        {
            String version = artifact.getVersion();

            if ( version.endsWith( SNAPSHOT ) )
            {
                String qualifier = snapshot.getTimestamp() + "-" + snapshot.getBuildNumber();
                version = version.substring( 0, version.length() - SNAPSHOT.length() ) + qualifier;
            }

            SnapshotVersion sv = new SnapshotVersion();
            sv.setClassifier( artifact.getClassifier() );
            sv.setVersion( version );
            sv.setUpdated( lastUpdated );
            versions.put( sv.getClassifier(), sv );
        }

        artifacts.clear();

        Versioning versioning = recessive.getVersioning();
        if ( versioning != null )
        {
            for ( SnapshotVersion sv : versioning.getSnapshotVersions() )
            {
                if ( !versions.containsKey( sv.getClassifier() ) )
                {
                    versions.put( sv.getClassifier(), sv );
                }
            }
        }

        metadata.getVersioning().setSnapshotVersions( new ArrayList<SnapshotVersion>( versions.values() ) );
    }

    private static int getBuildNumber( Metadata metadata )
    {
        int number = 0;

        Versioning versioning = metadata.getVersioning();
        if ( versioning != null )
        {
            Snapshot snapshot = versioning.getSnapshot();
            if ( snapshot != null && snapshot.getBuildNumber() > 0 )
            {
                number = snapshot.getBuildNumber();
            }
        }

        return number;
    }

    public String getGroupId()
    {
        return metadata.getGroupId();
    }

    public String getArtifactId()
    {
        return metadata.getArtifactId();
    }

    public String getVersion()
    {
        return metadata.getVersion();
    }

    public Nature getNature()
    {
        return Nature.SNAPSHOT;
    }

}
