/*
 *  Copyright (c) 2020 - 2023 nexyo
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       nexyo - initial API and implementation
 *
 */

package io.nexyo.edc.connector.edps;

import io.nexyo.edc.connector.edps.adapters.http.nexyo.info.NexyoHubInfoClient;
import io.nexyo.edc.connector.edps.extensions.SpaceMembershipParticipantAgentServiceExtension;
import io.nexyo.edc.connector.edps.handlers.policy.SpaceMembershipConstraintFunction;
import io.nexyo.edc.connector.edps.ports.http.DataSpaceApiController;
import io.nexyo.edc.connector.edps.ports.http.DidDocumentApiController;
import io.nexyo.edc.connector.edps.services.config.ConfigService;
import io.nexyo.edc.connector.edps.services.iam.dataspace.DataSpaceService;
import io.nexyo.edc.connector.edps.services.iam.did.DecentralizedIdentityService;
import org.eclipse.edc.connector.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.agent.ParticipantAgentService;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebService;

import java.time.Clock;

import static io.nexyo.edc.connector.edps.NexyoEdpsConstants.*;
import static io.nexyo.edc.connector.edps.NexyoEdpsSettings.DID_WEB_USE_HTTPS;
import static io.nexyo.edc.connector.edps.NexyoEdpsSettings.VAULT_KEYPAIR_IDENTIFIER;
import static org.eclipse.edc.policy.engine.spi.PolicyEngine.ALL_SCOPES;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;


/*
 * Provides an identity backed by DID + API to work with nexyo DataSpaces
 */
@Requires({Vault.class})
@Provides(IdentityService.class)
@Extension(value = NexyoEdpsExtension.NAME)
public class NexyoEdpsExtension implements ServiceExtension {
    public static final String NAME = "nexyo EDPS Extension";
    private Monitor monitor;
    @Inject
    private Vault vault;
    @Inject
    private Clock clock;
    @Inject
    private WebService webService;
    @Inject
    private EdcHttpClient httpClient;
    @Inject
    private TypeManager typeManager;
    @Inject
    private ParticipantAgentService participantAgentService;
    @Inject
    private RuleBindingRegistry ruleBindingRegistry;
    @Inject
    private PolicyEngine policyEngine;
    @Inject
    private PolicyDefinitionStore policyStore;
    private DecentralizedIdentityService identityService;
    private WebDidResolver webDidResolver;
    @Inject
    private DidResolverRegistry resolverRegistry;

    @Override
    public String name() {
        return "nexyo Data Space Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();
        monitor.info("Initializing nexyo NexyoEdpsExtension");

        var configService = new ConfigService(context);
        var mapper = typeManager.getMapper();

        var useHttpsSchema = Boolean.parseBoolean(configService.getRequiredConfigValue(DID_WEB_USE_HTTPS));
        this.webDidResolver = new WebDidResolver(httpClient, useHttpsSchema, mapper, monitor);

        monitor.info("registering did identity service");
        this.identityService = setupIdentityService(context);
        context.registerService(IdentityService.class, identityService);
        monitor.info("did identity service was registered successfully");


        monitor.info("registering spaceMembershipParticipantAgentServiceExtension");

        var dataSpaceService = new DataSpaceService(monitor, configService, webDidResolver, new NexyoHubInfoClient(httpClient, identityService, mapper, monitor));
        var spaceMembershipParticipantAgentServiceExtension = new SpaceMembershipParticipantAgentServiceExtension(monitor, dataSpaceService, configService);
        participantAgentService.register(spaceMembershipParticipantAgentServiceExtension);

        monitor.info("registering did document api");
        webService.registerResource(DID_API_ID, new DidDocumentApiController(monitor, identityService.generateDidDocument(), configService));

        monitor.info("registering dataspace api endpoints to mgmt api");

        webService.registerResource(MGMT_API_ID, new DataSpaceApiController(monitor, identityService, mapper, httpClient, webDidResolver, dataSpaceService));

        monitor.info("initializing nexyo DS membership policies ");
        ruleBindingRegistry.bind(ODRL_SCHEMA + "use", ALL_SCOPES);
        ruleBindingRegistry.bind(NEXYO_SPACE_MEMBERSHIP_CONSTRAINT_KEY, ALL_SCOPES);
        policyEngine.registerFunction(ALL_SCOPES, Permission.class, NEXYO_SPACE_MEMBERSHIP_CONSTRAINT_KEY, new SpaceMembershipConstraintFunction(this.monitor));
        monitor.info("nexyo Data Space Extension initialized");
    }


    @Override
    public void shutdown() {
        monitor.info("Shutting down nexyo HTTPTransferExtension");
    }

    /*
     * Creates an identityService with the given configuration.
     */
    private DecentralizedIdentityService setupIdentityService(ServiceExtensionContext context) {
        monitor.info("Initializing nexyo IdentityService Impl");

        var configService = new ConfigService(context);
        var keypairIdentifier = configService.getRequiredConfigValue(VAULT_KEYPAIR_IDENTIFIER);

        return new DecentralizedIdentityService(context, vault, monitor, clock, keypairIdentifier, this.webDidResolver, this.resolverRegistry);
    }
}
