/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.bean.builtin.ee;

import org.jboss.weld.manager.BeanManagerImpl;

import java.io.Serializable;
import java.util.concurrent.Callable;

public abstract class AbstractEECallable<V> implements Callable<V>, Serializable {

    private static final long serialVersionUID = 2685728358029843185L;

    private final BeanManagerImpl beanManager;

    protected AbstractEECallable(BeanManagerImpl beanManager) {
        this.beanManager = beanManager;
    }

    public BeanManagerImpl getBeanManager() {
        return beanManager;
    }

}