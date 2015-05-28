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

import static com.amashchenko.maven.plugin.gitflow.AbstractGitFlowMojo.LS;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow hotfix start mojo.
 *
 * @author Aleksandr Mashchenko
 *
 */
@Mojo(name = "hotfix-start", aggregator = true)
public class GitFlowHotfixStartMojo extends AbstractGitFlowMojo {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            String supportBranchName = gitFlowConfig.getProductionBranch();

            // git for-each-ref --format='%(refname:short)' refs/heads/feature/*
            final String supportBranches = gitFindBranches(gitFlowConfig
                    .getSupportBranchPrefix());

            if (!StringUtils.isBlank(supportBranches)) {

                final String[] branches = supportBranches.split("\\r?\\n");

                List<String> numberedList = new ArrayList<String>();
                StringBuilder str = new StringBuilder("Support branches:")
                        .append(LS);
                str.append((0) + ". " + gitFlowConfig.getProductionBranch() + LS);
                for (int i = 0; i < branches.length; i++) {
                    str.append((i + 1) + ". " + branches[i] + LS);
                    numberedList.add(String.valueOf(i + 1));
                }
                str.append("Choose support branch to hotfix");

                String featureNumber = null;
                try {
                    while (StringUtils.isBlank(featureNumber)) {
                        featureNumber = prompter.prompt(str.toString(),
                                numberedList);
                    }
                } catch (PrompterException e) {
                    getLog().error(e);
                }

                if (featureNumber != null) {
                    int num = Integer.parseInt(featureNumber);
                    if (num == 0) {
                        supportBranchName = gitFlowConfig.getProductionBranch();
                    } else {
                        supportBranchName = branches[num - 1];
                    }
                }

                if (StringUtils.isBlank(supportBranchName)) {
                    throw new MojoFailureException(
                            "Branch name to hotfix is blank.");
                }
            }

            // need to be in master to get correct project version
            // git checkout master
            gitCheckout(supportBranchName);

            String defaultVersion = "1.0.1";

            // get current project version from pom
            final String currentVersion = getCurrentProjectVersion();

            // get default hotfix version
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                        currentVersion);
                defaultVersion = versionInfo.getNextVersion()
                        .getReleaseVersionString();
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }

            String version = null;
            try {
                version = prompter.prompt("What is the hotfix version? ["
                        + defaultVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }

            if (StringUtils.isBlank(version)) {
                version = defaultVersion;
            }

            // git for-each-ref refs/heads/hotfix/...
            final String hotfixBranch = executeGitCommandReturn("for-each-ref",
                    "refs/heads/" + gitFlowConfig.getHotfixBranchPrefix()
                    + version);

            if (StringUtils.isNotBlank(hotfixBranch)) {
                throw new MojoFailureException(
                        "Hotfix branch with that name already exists. Cannot start hotfix.");
            }

            // git checkout -b hotfix/... master
//            gitCreateAndCheckout(gitFlowConfig.getHotfixBranchPrefix()
//                    + version, gitFlowConfig.getProductionBranch());
            gitFlowStart("hotfix", version, supportBranchName);

            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(version);

            // git commit -a -m updating poms for hotfix
            gitCommit("updating poms for hotfix");

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
