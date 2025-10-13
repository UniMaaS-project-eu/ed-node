package org.eclipse.edc.demo.dcp.ih;

import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredentialContainer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CredentialManager {

    public enum DuplicateStrategy {
        SKIP_IF_EXISTS,
        REPLACE_ALWAYS,
        KEEP_NEWEST,
        KEEP_OLDEST,
        ALLOW_DUPLICATES
    }

    private final CredentialStore store;
    private final Monitor monitor;

    public CredentialManager(CredentialStore store, Monitor monitor) {
        this.store = store;
        this.monitor = monitor;
    }

    public boolean storeCredential(VerifiableCredentialResource credential, DuplicateStrategy strategy) {
        var existing = findByType(credential);

        if (!existing.isEmpty()) {
            switch (strategy) {
                case SKIP_IF_EXISTS -> {
                    monitor.info("VC of same type exists, skipping store.");
                    return false;
                }
                case REPLACE_ALWAYS, KEEP_NEWEST -> {
                    existing.forEach(vc -> {
                        var deleteResult = store.deleteById(vc.getId());
                        if (!deleteResult.succeeded()) {
                            monitor.warning("Failed to delete VC " + vc.getId() + ": " + deleteResult.getFailureDetail());
                        } else {
                            monitor.info("Deleted VC: " + vc.getId());
                        }
                    });
                    store.create(credential);
                    return true;
                }
                case KEEP_OLDEST -> {
                    monitor.info("VC already exists, keeping oldest one.");
                    return false;
                }
                case ALLOW_DUPLICATES -> {
                    store.create(credential);
                    return true;
                }
            }
        } else {
            store.create(credential);
            return true;
        }
        return false;
    }

    private Collection<VerifiableCredentialResource> findByType(VerifiableCredentialResource credential) {
        StoreResult<Collection<VerifiableCredentialResource>> result = store.query(QuerySpec.none());
        if (!result.succeeded()) {
            monitor.warning("Query failed: " + result.getFailureDetail());
            return Collections.emptyList();
        }

        // Extraer el tipo de la nueva credencial
        VerifiableCredentialContainer newContainer = credential.getVerifiableCredential();
        VerifiableCredential newVc = newContainer.credential();
        List<String> newTypes = newVc.getType();

        return result.getContent().stream()
                .filter(vc -> {
                    try {
                        var existingVc = vc.getVerifiableCredential().credential();
                        var existingTypes = existingVc.getType();
                        return existingTypes != null && newTypes != null &&
                                existingTypes.equals(newTypes);
                    } catch (Exception e) {
                        monitor.warning("Error comparing VC types: " + e.getMessage());
                        return false;
                    }
                })
                .toList();
    }
}
