/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.web.templ;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.ext.web.templ.impl.Thymeleaf3TemplateEngineImpl;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * A template engine that uses the Thymeleaf library.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Thymeleaf3TemplateEngine extends TemplateEngine {
    TemplateMode DEFAULT_TEMPLATE_MODE = TemplateMode.HTML;

    /**
     * Create a template engine using defaults
     *
     * @return  the engine
     */
    static Thymeleaf3TemplateEngine create() {
        return new Thymeleaf3TemplateEngineImpl();
    }

    /**
     * Set the mode for the engine
     *
     * @param mode  the mode
     * @return a reference to this for fluency
     */
    Thymeleaf3TemplateEngine setMode(TemplateMode mode);

    /**
     * Get a reference to the internal Thymeleaf TemplateEngine object so it
     * can be configured.
     *
     * @return a reference to the internal Thymeleaf TemplateEngine instance.
     */
    @GenIgnore
    org.thymeleaf.TemplateEngine getThymeleafTemplateEngine();
}
