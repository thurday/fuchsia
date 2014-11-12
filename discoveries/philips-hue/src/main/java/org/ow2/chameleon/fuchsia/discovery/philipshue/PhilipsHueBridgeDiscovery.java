package org.ow2.chameleon.fuchsia.discovery.philipshue;

/*
 * #%L
 * OW2 Chameleon - Fuchsia Discovery Philips Hue
 * %%
 * Copyright (C) 2009 - 2014 OW2 Chameleon
 * %%
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
 * #L%
 */

import com.philips.lighting.hue.sdk.*;
import com.philips.lighting.hue.sdk.connection.impl.PHBridgeInternal;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHHueError;
import org.apache.felix.ipojo.annotations.*;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.ow2.chameleon.fuchsia.core.component.AbstractDiscoveryComponent;
import org.ow2.chameleon.fuchsia.core.component.DiscoveryService;
import org.ow2.chameleon.fuchsia.core.declaration.ImportDeclaration;
import org.ow2.chameleon.fuchsia.core.declaration.ImportDeclarationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.apache.felix.ipojo.Factory.INSTANCE_NAME_PROPERTY;

@Component
@Provides(specifications = {PhilipsDiscoveryService.class,DiscoveryService.class})
@Instantiate
public class PhilipsHueBridgeDiscovery extends AbstractDiscoveryComponent implements PHSDKListener,PhilipsDiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(PhilipsHueBridgeDiscovery.class);

    private static final String EVENT_CACHE_UPDATED="philips/hue/bridge/cache_updated";

    @ServiceProperty(name = INSTANCE_NAME_PROPERTY)
    private String name;

    @ServiceProperty(name = "philips.hue.discovery.pooling", value = "10000")
    private Long pollingTime;

    private Preferences preferences = PhilipsPreference.getInstance();//Preferences.userRoot().node(this.getClass().getName());

    private Map<String, ImportDeclaration> ipImportDeclarationMap = new HashMap<String, ImportDeclaration>();

    private Set<String> ipAuthenticationInProgress = new HashSet<String>();

    private PHHueSDK philipsSDK;

    private BridgeSearchScheduler bridgeSearchScheduler;

    @Requires
    EventAdmin eventAdmin;

    public PhilipsHueBridgeDiscovery(BundleContext bundleContext) {
        super(bundleContext);
    }

    @Validate
    public void start() {
        LOG.info("Philips Hue discovery is up and running.");
        philipsSDK = PHHueSDK.getInstance();
        philipsSDK.getNotificationManager().registerSDKListener(this);
        bridgeSearchScheduler=new BridgeSearchScheduler(this.pollingTime);
        philipsSDK.getNotificationManager().registerSDKListener(bridgeSearchScheduler);
        bridgeSearchScheduler.activate();
    }

    @Invalidate
    public void stop() {
        philipsSDK.destroySDK();
        bridgeSearchScheduler.desactivate();
    }

    public String getName() {
        return name;
    }

    public void onCacheUpdated(int i, PHBridge phBridge) {
        Dictionary metatable = new Hashtable();
        metatable.put("bridge", phBridge);
        Event eventAdminMessage = new Event(EVENT_CACHE_UPDATED, metatable);
        eventAdmin.sendEvent(eventAdminMessage);
    }

    public void onBridgeConnected(PHBridge phBridge) {

        String bridgeIP = phBridge.getResourceCache().getBridgeConfiguration().getIpAddress();

        LOG.info("Removing brigde {} from the list of authentication in progress, there {} bridges in authentication progress", bridgeIP, ipAuthenticationInProgress.size());
        ipAuthenticationInProgress.remove(bridgeIP);

        LOG.info("Fetching IP {} for connection", bridgeIP);

        philipsSDK.enableHeartbeat(phBridge, pollingTime);
        philipsSDK.setSelectedBridge(phBridge);
        ImportDeclaration declaration = generateImportDeclaration(phBridge);
        super.registerImportDeclaration(declaration);
        ipImportDeclarationMap.put(bridgeIP, declaration);

    }

    public void onAuthenticationRequired(PHAccessPoint phAccessPoint) {

        final String MSG = "authentication required, you have 30 seconds to push the button on the bridge";

        LOG.warn(MSG);

        Dictionary metatable = new Hashtable();
        metatable.put("message", MSG);
        metatable.put("ip", phAccessPoint.getIpAddress());
        metatable.put("mac", phAccessPoint.getMacAddress());

        Event eventAdminMessage = new Event("philips/hue/bridge/authentication_required", metatable);

        eventAdmin.sendEvent(eventAdminMessage);

        ipAuthenticationInProgress.add(phAccessPoint.getIpAddress());

        philipsSDK.startPushlinkAuthentication(phAccessPoint);
    }

    public void onAccessPointsFound(List<PHAccessPoint> phAccessPoints) {
        for (PHAccessPoint foundAP : phAccessPoints) {
            if (!philipsSDK.isAccessPointConnected(foundAP)) {
                LOG.trace("AP not connected.");

                LOG.info("Auth in progress for " + foundAP.getIpAddress());
                for (String value : ipAuthenticationInProgress) {
                    LOG.info("\tIP:" + value);
                }
                LOG.info("/Auth in progress:");

                if (!ipAuthenticationInProgress.contains(foundAP.getIpAddress())) {
                    LOG.trace("Requesting connection for " + foundAP.getIpAddress());
                    ipAuthenticationInProgress.add(foundAP.getIpAddress());
                    connect(foundAP);
                } else {
                    LOG.trace("not requesting connection for {}, it was already in progress", foundAP.getIpAddress());
                }
            } else {
                LOG.trace("access point already connected {}",foundAP.getIpAddress());
            }
        }
    }

    public void onSuccess() {

        LOG.trace("Connected with success");

    }

    public void onError(int code, String s) {
        LOG.trace("Bridge failed with the code {} and message {}",code,s);

        if (code == PHHueError.BRIDGE_NOT_RESPONDING) {
        }else if (code == PHMessageType.PUSHLINK_BUTTON_NOT_PRESSED) {
        }else if (code == PHMessageType.PUSHLINK_AUTHENTICATION_FAILED) {
            LOG.trace("Button not pressed, code {}",code);
            ipAuthenticationInProgress.clear();
            Dictionary metatable = new Hashtable();
            metatable.put("message", s);
            metatable.put("code", code);
            Event eventAdminMessage = new Event("philips/hue/bridge/error", metatable);
            eventAdmin.sendEvent(eventAdminMessage);
        }else if (code == PHMessageType.BRIDGE_NOT_FOUND) {
        }

    }

    public void onStateUpdate(Hashtable<String, String> hashtable, List<PHHueError> phHueErrors) {
        // not used
        LOG.trace("State updated {} and errors {}",hashtable,phHueErrors);
    }

    public void onConnectionResumed(PHBridge phBridge) {

        LOG.trace("Connection resumed with the bridge {}",phBridge);

        //LOG.info("Connection resumed with bridge {}",phBridge.getResourceCache().getBridgeConfiguration().getIpAddress());

    }

    public void onConnectionLost(PHAccessPoint phAccessPoint) {

        LOG.trace("Fetching IP {} for disconnection", phAccessPoint.getIpAddress());

        ImportDeclaration declaration = ipImportDeclarationMap.get(phAccessPoint.getIpAddress());

        if (declaration != null) {
            super.unregisterImportDeclaration(declaration);
        } else {
            LOG.warn("No such ip found for disconnection");
        }

    }

    public void searchForBridges() {

        LOG.trace("Searching for bridges..");

        PHBridgeSearchManager sm = (PHBridgeSearchManager) philipsSDK.getSDKService(PHHueSDK.SEARCH_BRIDGE);
        //Search UPnP but skips the portal and ip address
        sm.search(true, false, false);
    }

    private ImportDeclaration generateImportDeclaration(PHBridge bridge) {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("id", "bridge-"+bridge.getResourceCache().getBridgeConfiguration().getBridgeID());
        metadata.put("discovery.philips.bridge.type", PHBridge.class.getName());
        metadata.put("discovery.philips.bridge.object", bridge);
        metadata.put("scope", "generic");

        return ImportDeclarationBuilder.fromMetadata(metadata).build();
    }

    private void connect(PHAccessPoint ap) {

        LOG.trace("Asking connection for the AP {}", ap);

        final String bridgeUsernameKey = "username";
        String username = preferences.get(bridgeUsernameKey, null);
        if (username == null) {
            username = PHBridgeInternal.generateUniqueKey();
            preferences.put(bridgeUsernameKey, username);
            try {
                preferences.flush();
            } catch (BackingStoreException e) {
                LOG.error("failed to store username in java preferences, this will force you to push the bridge button everytime to authenticate", e);
            }
        }
        ap.setUsername(username);

        philipsSDK.connect(ap);

    }

}
