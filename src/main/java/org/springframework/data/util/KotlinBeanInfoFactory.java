/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.util;

import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KMutableProperty;
import kotlin.reflect.KProperty;
import kotlin.reflect.jvm.ReflectJvmMapping;

import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanInfoFactory;
import org.springframework.core.KotlinDetector;
import org.springframework.core.Ordered;

/**
 * {@link BeanInfoFactory} specific to Kotlin types using Kotlin reflection to determine bean properties.
 *
 * @author Mark Paluch
 * @author Yanming Zhou
 * @since 3.2
 * @see JvmClassMappingKt
 * @see ReflectJvmMapping
 */
public class KotlinBeanInfoFactory implements BeanInfoFactory, Ordered {

	@Override
	public BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException {

		if (beanClass.isInterface()) {
			return null; // back-off to leave interface-based properties to the default mechanism.
		}

		if (!KotlinDetector.isKotlinReflectPresent() || !KotlinDetector.isKotlinType(beanClass)) {
			return null;
		}


		KClass<?> kotlinClass = JvmClassMappingKt.getKotlinClass(beanClass);
		List<PropertyDescriptor> pds = new ArrayList<>();

		for (KCallable<?> member : kotlinClass.getMembers()) {

			if (member instanceof KProperty<?> property) {

				// Kotlin introduce auto-generated "entries" property for enum class since 1.8.20 and stable from 1.9.0
				// see https://youtrack.jetbrains.com/issue/KT-48872
				if (beanClass.isEnum() && property.getName().equals("entries")) {
					continue;
				}

				Method getter = ReflectJvmMapping.getJavaGetter(property);
				Method setter = property instanceof KMutableProperty<?> kmp ? ReflectJvmMapping.getJavaSetter(kmp) : null;

				pds.add(new PropertyDescriptor(property.getName(), getter, setter));
			}
		}
		return new SimpleBeanInfo() {
			@Override
			public BeanDescriptor getBeanDescriptor() {
				return new BeanDescriptor(beanClass);
			}

			@Override
			public PropertyDescriptor[] getPropertyDescriptors() {
				return pds.toArray(new PropertyDescriptor[0]);
			}
		};
	}

	@Override
	public int getOrder() {
		return LOWEST_PRECEDENCE - 10; // leave some space for customizations.
	}

}
