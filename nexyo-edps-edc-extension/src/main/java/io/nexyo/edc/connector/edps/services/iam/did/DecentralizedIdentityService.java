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

package io.nexyo.edc.connector.edps.services.iam.did;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import io.nexyo.edc.connector.edps.services.config.ConfigService;
import io.nexyo.edc.connector.edps.services.iam.did.web.WebDidUrlResolver;
import org.eclipse.edc.iam.did.crypto.JwtUtils;
import org.eclipse.edc.iam.did.crypto.key.EcPrivateKeyWrapper;
import org.eclipse.edc.iam.did.crypto.key.KeyConverter;
import org.eclipse.edc.iam.did.spi.document.DidConstants;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.document.VerificationMethod;
import org.eclipse.edc.iam.did.spi.key.PrivateKeyWrapper;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

import static io.nexyo.edc.connector.edps.NexyoEdpsSettings.*;
import static org.eclipse.edc.spi.agent.ParticipantAgentService.DEFAULT_IDENTITY_CLAIM_KEY;

/*
 * Provides an identity backed by DID.
 */
public class DecentralizedIdentityService implements IdentityService {
    public static final String NAME = "Decentralized Identity Service";
    public static final Curve CURVE = Curve.P_256;
    private ECKey keyPair;

    private final String keyPairIdentifier;
    private final DidResolverRegistry resolverRegistry;
    private final Monitor monitor;
    private final Vault vault;
    private final Clock clock;

    private ServiceExtensionContext context;

    private String identity;

    private final ConfigService configService;

    private WebDidResolver webDidResolver;

    public DecentralizedIdentityService(ServiceExtensionContext context, Vault vault, Monitor monitor, Clock clock, String keyPairIdentifier, WebDidResolver webDidResolver, DidResolverRegistry resolverRegistry) {
        monitor.info("Initializing nexyo DecentralizedIdentityService");

        this.monitor = monitor;
        this.clock = clock;
        this.vault = vault;
        this.context = context;
        this.webDidResolver = webDidResolver;
        this.resolverRegistry = resolverRegistry;

        configService = new ConfigService(context);
        var connectorHostname = configService.getRequiredConfigValue(EDC_HOSTNAME);
        monitor.info("connectorHostname: " + connectorHostname);
        var didDocumentPath = configService.getRequiredConfigValue(DID_API_PATH);
        monitor.info("connectorHostname: " + connectorHostname);
        identity = generateConnectorDidWeb(connectorHostname, didDocumentPath);

        var connectorDID = configService.getRequiredConfigValue(DID_URL);
        monitor.info("connectorDID from config " + connectorDID);
        if (!identity.equals(connectorDID)) {
            throw new EdcException("connectorDID from config " + connectorDID + " is different from generated connectorDidWeb " + identity);
        }

        this.keyPairIdentifier = keyPairIdentifier;

        if (!this.isKeyPairInVault()) {
            monitor.warning("No secrets for given keypair id set, generating new key pair");
            try {
                var keyPair = generateECKey();
                this.saveKeyPairToVault(keyPair);
            } catch (Exception e) {
                throw new EdcException("Couldn't generate key pair", e);
            }
        }

        this.keyPair = this.retrieveKeysFromVault();

        monitor.info("Identity " + identity + " loaded");
    }

    private ECKey retrieveKeysFromVault() throws EdcException {
        try {
            String keyPairBase64 = vault.resolveSecret(keyPairIdentifier);
            var keyPairString = new String(Base64.getDecoder().decode(keyPairBase64));
            keyPair = ECKey.parse(keyPairString);

            return keyPair;
        } catch (Exception e) {
            throw new EdcException("Couldn't retrieve keys from vault: " + e.getMessage());
        }
    }

    private void saveKeyPairToVault(@NotNull ECKey keyPair) throws EdcException {
        var keyPairJSON = keyPair.toJSONObject().toString();
        var keyPairJSONB64 = Base64.getEncoder().encodeToString(keyPairJSON.getBytes());

        var keyPairResult = vault.storeSecret(keyPairIdentifier, keyPairJSONB64);
        if (keyPairResult.failed()) {
            throw new EdcException("Couldn't store private key: " + keyPairResult.getFailureMessages());
        }
    }

    private boolean isKeyPairInVault() {
        var keyPair = vault.resolveSecret(keyPairIdentifier);

        return keyPair != null && !keyPair.isBlank();
    }

    private ECKey generateECKey() throws EdcException {
        var keyGenerator = new ECKeyGenerator(CURVE);

        ECKey ecKey;
        try {
            ecKey = keyGenerator.keyID(keyPairIdentifier).generate();
        } catch (Exception e) {
            throw new EdcException("Couldn't generate key pair", e);
        }

        return ecKey;
    }

    private static String generateConnectorDidWeb(String connectorHostname, String didDocumentPath) {
        if (didDocumentPath.equals("/")) {
            return ("did:web:" + connectorHostname);
        }
        var didUrl = "did:web:" + connectorHostname + didDocumentPath.replace("/", ":");
        // Check if the last character of the string is ':'
        if (didUrl.charAt(didUrl.length() - 1) == ':') {
            // If yes, remove the last character
            didUrl = didUrl.substring(0, didUrl.length() - 1);
        }
        return didUrl;
    }

    private String generateConnectorDidWebHttpUrl() {
        String useHTTPsString = this.configService.getRequiredConfigValue(DID_WEB_USE_HTTPS);
        boolean useHTTPS = Boolean.parseBoolean(useHTTPsString);
        var httpUrl = new WebDidUrlResolver(useHTTPS, monitor).apply(this.identity).toString();
        return httpUrl;
    }

    public DidDocument generateDidDocument() {
        var firstMethodPublicKeyJwk =  this.keyPair.toPublicJWK().toJSONObject();
        var firstMethod = VerificationMethod.Builder.create().controller(identity).id(identity + "#key-1").type("JsonWebKey2020").publicKeyJwk(firstMethodPublicKeyJwk).build();

        var verificationMethods = new ArrayList<VerificationMethod>(1);
        verificationMethods.add(firstMethod);

        var services = new ArrayList<Service>();
        services.add(getDspService());
        services.add(getOrganizationInfoService());

        return DidDocument.Builder.newInstance().id(identity).verificationMethod(verificationMethods).service(services).build();
    }

    private Service getDspService() {
        var id = identity + "#DataSpaceProtocol";
        var type = "DataSpaceProtocolService";
        var serviceEndpoint = configService.getRequiredConfigValue(DSP_CALLBACK_ADDRESS);
        // TODO validate if url

        return new Service(id, type, serviceEndpoint);
    }

    private Service getOrganizationInfoService() {
        var id = identity + "#OrganizationInfo";
        var type = "SelfDescriptionService";
        var serviceEndpoint = this.generateConnectorDidWebHttpUrl().replace("/.well-known", "").replace("did.json", "organization");

        return new Service(id, type, serviceEndpoint);
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(TokenParameters parameters) throws EdcException{
        String jwtSubject = this.identity;
        String jwtIssuer = this.identity;
        PrivateKeyWrapper privateKey = new EcPrivateKeyWrapper(this.keyPair);

        var jwt = JwtUtils.create(privateKey, jwtIssuer, jwtSubject, parameters.getAudience(), this.clock);
        var token = jwt.serialize();
        return Result.success(TokenRepresentation.Builder.newInstance().token(token).build());
    }

    @Override
    public Result<ClaimToken> verifyJwtToken(TokenRepresentation tokenRepresentation, String audience){
        try {
            var jwt = SignedJWT.parse(tokenRepresentation.getToken());
            monitor.debug("Starting verification...");

            monitor.debug("Resolving other party's DID Document");
            var didResult = resolverRegistry.resolve(jwt.getJWTClaimsSet().getIssuer());
            if (didResult.failed()) {
                return Result.failure("Unable to resolve DID: " + String.join(", ", didResult.getFailureMessages()));
            }
            monitor.debug("Extracting public key");

            // this will return the _first_ public key entry
            var publicKey = firstVerificationMethod(didResult.getContent());
            if (publicKey.isEmpty()) {
                return Result.failure("Public Key not found in DID Document!");
            }

            //convert the POJO into a usable PK-wrapper:
            var publicKeyJwk = publicKey.get().getPublicKeyJwk();
            var publicKeyWrapperResult = KeyConverter.toPublicKeyWrapper(publicKeyJwk, publicKey.get().getId());
            if (publicKeyWrapperResult.failed()) {
                monitor.debug("Failed to convert JWK into public key wrapper");
                return publicKeyWrapperResult.mapTo();
            }

            monitor.debug("Verifying JWT with public key...");
            var verified = JwtUtils.verify(jwt, publicKeyWrapperResult.getContent(), audience);
            if (verified.failed()) {
                monitor.debug(() -> "Failure in token verification: " + verified.getFailureDetail());
                return Result.failure("Token could not be verified!");
            }

            monitor.debug("Building ClaimToken");
            var claimToken = ClaimToken.Builder.newInstance()
                    .claim(DEFAULT_IDENTITY_CLAIM_KEY, jwt.getJWTClaimsSet().getIssuer())
                    .build();

            return Result.success(claimToken);
        } catch (ParseException e) {
            monitor.severe("Error parsing JWT", e);
            return Result.failure("Error parsing JWT");
        }
    }

    public String getParticipantId(){
        return this.identity;
    }

    @NotNull
    private Optional<VerificationMethod> firstVerificationMethod(DidDocument did) {
        return did.getVerificationMethod().stream()
                .filter(vm -> DidConstants.ALLOWED_VERIFICATION_TYPES.contains(vm.getType()))
                .findFirst();
    }
}
