/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.karaf.deployer.features;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.felix.fileinstall.ArtifactUrlTransformer;
import org.apache.felix.karaf.features.Feature;
import org.apache.felix.karaf.features.FeaturesService;
import org.apache.felix.karaf.features.Repository;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * A deployment listener able to hot deploy a feature descriptor
 */
public class FeatureDeploymentListener implements ArtifactUrlTransformer, SynchronousBundleListener {

    public static final String FEATURE_PATH = "org.apache.felix.karaf.shell.features";

    private static final Log LOGGER = LogFactory.getLog(FeatureDeploymentListener.class);

    private DocumentBuilderFactory dbf;
    private FeaturesService featuresService;
    private BundleContext bundleContext;

    public void setFeaturesService(FeaturesService featuresService) {
        this.featuresService = featuresService;
    }

    public FeaturesService getFeaturesService() {
        return featuresService;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void init() throws Exception {
        bundleContext.addBundleListener(this);
        for (Bundle bundle : bundleContext.getBundles()) {
            bundleChanged(new BundleEvent(BundleEvent.INSTALLED, bundle));
        }
    }

    public void destroy() throws Exception {
        bundleContext.removeBundleListener(this);
    }

    public boolean canHandle(File artifact) {
        try {
            if (artifact.isFile() && artifact.getName().endsWith(".xml")) {
                Document doc = parse(artifact);
                String name = doc.getDocumentElement().getLocalName();
                String uri  = doc.getDocumentElement().getNamespaceURI();
                if ("features".equals(name) && (uri == null || "".equals(uri))) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unable to parse deployed file " + artifact.getAbsolutePath(), e);
        }
        return false;
    }

    public URL transform(URL artifact) {
        // We can't really install the feature right now and just return nothing.
        // We would not be aware of the fact that the bundle has been uninstalled
        // and therefore require the feature to be uninstalled.
        // So instead, create a fake bundle with the file inside, which will be listened by
        // this deployer: installation / uninstallation of the feature will be done
        // while the bundle is installed / uninstalled.
        try {
            return new URL("feature", null, artifact.toString());
        } catch (Exception e) {
            LOGGER.error("Unable to build feature bundle", e);
            return null;
        }
    }

    public void bundleChanged(BundleEvent bundleEvent) {
        try {
            Bundle bundle = bundleEvent.getBundle();
            if (bundleEvent.getType() == BundleEvent.RESOLVED) {
                Enumeration featuresUrlEnumeration = bundle.findEntries("/META-INF/" + FEATURE_PATH + "/", "*.xml", false);
                while (featuresUrlEnumeration != null && featuresUrlEnumeration.hasMoreElements()) {
                    URL url = (URL) featuresUrlEnumeration.nextElement();
                    featuresService.addRepository(url.toURI());
                    for (Repository repo : featuresService.listRepositories()) {
                        if (repo.getURI().equals(url.toURI())) {
                            Set<Feature> features = new HashSet<Feature>(Arrays.asList(repo.getFeatures()));
                            try {
                                featuresService.installFeatures(features, EnumSet.noneOf(FeaturesService.Option.class));
                            } catch (Exception e) {
                                LOGGER.error("Unable to install features", e);
                            }
                        }
                    }
                }
            } else if (bundleEvent.getType() == BundleEvent.UNINSTALLED) {
                Enumeration featuresUrlEnumeration = bundle.findEntries("/META-INF/" + FEATURE_PATH + "/", "*.xml", false);
                while (featuresUrlEnumeration != null && featuresUrlEnumeration.hasMoreElements()) {
                    URL url = (URL) featuresUrlEnumeration.nextElement();
                    for (Repository repo : featuresService.listRepositories()) {
                        if (repo.getURI().equals(url.toURI())) {
                            for (Feature f : repo.getFeatures()) {
                                try {
                                    featuresService.uninstallFeature(f.getName(), f.getVersion());
                                } catch (Exception e) {
                                    LOGGER.error("Unable to uninstall feature: " + f.getName(), e);
                                }
                            }
                        }
                    }
                    featuresService.removeRepository(url.toURI());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unable to install / uninstall feature", e);
        }
    }

    protected Document parse(File artifact) throws Exception {
        if (dbf == null) {
            dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException exception) throws SAXException {
            }
            public void error(SAXParseException exception) throws SAXException {
            }
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return db.parse(artifact);
    }

}
