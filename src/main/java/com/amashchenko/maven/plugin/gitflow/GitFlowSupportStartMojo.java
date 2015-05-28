/*
 * Copyright 2014-2015 Aleksandr Mashchenko.
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
package com.amashchenko.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow support start mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "support-start", aggregator = true)
public class GitFlowSupportStartMojo extends AbstractGitFlowMojo {

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            // need to be in master to get correct project version
            // git checkout master
            gitCheckout(gitFlowConfig.getProductionBranch());

            String defaultVersion = "1.0.1";

            // get current project version from pom
            final String currentVersion = getCurrentProjectVersion();

            // get default support version
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                        currentVersion);
                final DefaultVersionInfo supVersion = new DefaultVersionInfo(
                        versionInfo.getDigits().subList(0, 2), null, null, null, null, null, null);
                defaultVersion = supVersion.getReleaseVersionString();
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }

            String version = null;
            if (settings.isInteractiveMode()) {
                try {
                    version = prompter.prompt("What is the support version? ["
                            + defaultVersion + "]");
                } catch (PrompterException e) {
                    getLog().error(e);
                }
            }

            if (StringUtils.isBlank(version)) {
                version = defaultVersion;
            }

            // git for-each-ref refs/heads/support/...
            final String supportBranch = executeGitCommandReturn("for-each-ref",
                    "refs/heads/" + gitFlowConfig.getSupportBranchPrefix()
                            + version);

            if (StringUtils.isNotBlank(supportBranch)) {
                throw new MojoFailureException(
                        "Support branch with that name already exists. Cannot start support.");
            }

            // git checkout -b support/... master
//            gitCreateAndCheckout(gitFlowConfig.getSupportBranchPrefix()
//                    + version, gitFlowConfig.getProductionBranch());

            gitFlowStart("support", version, "master");
            
            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(version);

            // git commit -a -m updating poms for support
            gitCommit("updating poms for support");
            System.out.println("Committed.");
            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
