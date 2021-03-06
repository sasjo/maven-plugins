package org.apache.maven.plugin.deploy;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @version $Id$
 */
public abstract class AbstractDeployMojo
    extends AbstractMojo
{
    /**
     */
    @Component
    private ArtifactDeployer deployer;

    /**
     * Component used to create an artifact.
     *
     * @component
     */
    @Component
    protected ArtifactFactory artifactFactory;

    /**
     * Component used to create a repository.
     *
     * @component
     */
    @Component
    ArtifactRepositoryFactory repositoryFactory;

    /**
     * Map that contains the layouts.
     */
    @Component( role = ArtifactRepositoryLayout.class )
    private Map repositoryLayouts;

    /**
     */
    @Parameter( defaultValue = "${localRepository}", required = true, readonly = true )
    private ArtifactRepository localRepository;

    /**
     * Flag whether Maven is currently in online/offline mode.
     */
    @Parameter( defaultValue = "${settings.offline}", readonly = true )
    private boolean offline;

    /**
     * Parameter used to update the metadata to make the artifact as release.
     */
    @Parameter( property = "updateReleaseInfo", defaultValue = "false" )
    protected boolean updateReleaseInfo;

    /**
     * Parameter used to control how many times a failed deployment will be retried before giving up and failing.
     * If a value outside the range 1-10 is specified it will be pulled to the nearest value within the range 1-10.
     *
     * @since 2.7
     */
    @Parameter( property = "retryFailedDeploymentCount", defaultValue = "1" )
    private int retryFailedDeploymentCount;

    /* Setters and Getters */

    public ArtifactDeployer getDeployer()
    {
        return deployer;
    }

    public void setDeployer( ArtifactDeployer deployer )
    {
        this.deployer = deployer;
    }

    public ArtifactRepository getLocalRepository()
    {
        return localRepository;
    }

    public void setLocalRepository( ArtifactRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    void failIfOffline()
        throws MojoFailureException
    {
        if ( offline )
        {
            throw new MojoFailureException( "Cannot deploy artifacts when Maven is in offline mode" );
        }
    }

    ArtifactRepositoryLayout getLayout( String id )
        throws MojoExecutionException
    {
        ArtifactRepositoryLayout layout = (ArtifactRepositoryLayout) repositoryLayouts.get( id );

        if ( layout == null )
        {
            throw new MojoExecutionException( "Invalid repository layout: " + id );
        }

        return layout;
    }

    /**
     * Deploy an artifact from a particular file.
     *
     * @param source the file to deploy
     * @param artifact the artifact definition
     * @param deploymentRepository the repository to deploy to
     * @param localRepository the local repository to install into
     * @throws ArtifactDeploymentException if an error occurred deploying the artifact
     */
    protected void deploy( File source, Artifact artifact, ArtifactRepository deploymentRepository,
                           ArtifactRepository localRepository )
        throws ArtifactDeploymentException
    {
        int retryFailedDeploymentCount = Math.max( 1, Math.min( 10, this.retryFailedDeploymentCount ) );
        ArtifactDeploymentException exception = null;
        for ( int count = 0; count < retryFailedDeploymentCount; count++ )
        {
            try
            {
                if (count > 0)
                {
                    getLog().info(
                        "Retrying deployment attempt " + ( count + 1 ) + " of " + retryFailedDeploymentCount );
                }
                getDeployer().deploy( source, artifact, deploymentRepository, localRepository );
                exception = null;
                break;
            }
            catch ( ArtifactDeploymentException e )
            {
                if (count + 1 < retryFailedDeploymentCount) {
                    getLog().warn( "Encountered issue during deployment: " + e.getLocalizedMessage());
                    getLog().debug( e );
                }
                if ( exception == null )
                {
                    exception = e;
                }
            }
        }
        if ( exception != null )
        {
            throw exception;
        }
    }
}
