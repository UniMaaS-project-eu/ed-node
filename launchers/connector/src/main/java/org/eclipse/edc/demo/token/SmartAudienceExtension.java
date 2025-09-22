/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 */

package org.eclipse.edc.demo.token;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.token.spi.TokenDecoratorRegistry;

/**
 * Extension que configura el sistema de audience dinámico para EDC 0.14.0.
 */
@Extension("Smart Audience Token Decorator")
public class SmartAudienceExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "Smart Audience Token Decorator";
    private static final String DEFAULT_AUDIENCE_SETTING = "edc.token.audience.default";
    private static final String DEFAULT_AUDIENCE_VALUE = "edc:default";

    @Inject
    private TokenDecoratorRegistry tokenDecoratorRegistry;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        
        // Obtener audience por defecto de la configuración
        String defaultAudience = context.getSetting(DEFAULT_AUDIENCE_SETTING, DEFAULT_AUDIENCE_VALUE);
        
        // Crear y registrar el TokenDecorator
        var audienceDecorator = new MinimalAudienceTokenDecorator(defaultAudience);
        
        // En EDC 0.14.0, el método register requiere un String como primer parámetro
        tokenDecoratorRegistry.register("smart-audience-decorator", audienceDecorator);
        
        monitor.info("✅ Registered MinimalAudienceTokenDecorator with default audience: " + defaultAudience);
        monitor.info("🎉 Smart Audience TokenDecorator is now active!");
        monitor.info("📋 To set audience dynamically, use: MinimalAudienceTokenDecorator.setCounterPartyId(counterPartyId)");
    }
}