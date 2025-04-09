# nexyo Data Space EDC Extension

## General

## Restrictions

### DID

On startup of your application using this extension, a Dezentralized Identity (DID) is created alongside a DID document. The DID document is published at the path defined by the `edc.identity.did.url` property.

The DID and the document will be managed by the Dezentralized Identity Service.


The DID document shows the identites public key. The public/private keypair is stored in the vault of the connector with the id defined in field `edc.nexyodataspace.did.keypair.id`. The vault is defined by the `edc.vault` property.

Before startup the following variables have to be set in your `config.properties` file:

```properties
edc.hostname=public-connector.local
edc.identity.did.url=did:web:public-connector.local
web.http.did.port=9000 # make sure this internal ports is mapped to port 443 by your reverse proxy
web.http.did.path=/
edc.vault=/app/dataspaceconnector-vault.properties
edc.nexyodataspace.did.keypair.id=did-keypair-1
edc.nexyodataspace.organization.name="ACME ORG"
edc.nexyodataspace.organization.description="We sell stuff"
edc.nexyodataspace.organization.website="https://acme.org"
edc.iam.did.web.use.https=true
```

The edc.identity.did.url has to match the hostname of your connector + the path to the DID document. The path is defined by the `web.http.did.path` property.

If you have a reverse proxy in front of your connector, make sure that the internal port `web.http.did.port` is mapped to port 443 by your reverse proxy.
