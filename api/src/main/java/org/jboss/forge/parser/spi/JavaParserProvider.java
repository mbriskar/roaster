/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.forge.parser.spi;

import java.io.InputStream;

import org.jboss.forge.parser.java.ReadJavaSource;
import org.jboss.forge.parser.java.ReadJavaSource.JavaSource;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * 
 */
public interface JavaParserProvider
{
   /**
    * Create a new empty {@link JavaSource} instance.
    * 
    * @param type desired source type
    * @return instance of {@code T}, {@code null} if the {@link JavaSource} type is not supported by this
    *         {@link JavaParserProvider}.
    */
   public <T extends JavaSource<?>> T create(final Class<T> type);

   /**
    * Read the given {@link InputStream} and parse the data into a new {@link ReadJavaSource} instance.
    * 
    * @param data to parse
    * @return {@link ReadJavaSource}, {@code null} if the data format is not recognized by this {@link JavaParserProvider}.
    */
   public ReadJavaSource<?> parse(final InputStream data);

}