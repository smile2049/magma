/*
 * Copyright (c) 2019 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;

public abstract class AttributeAwareBuilder<T extends AttributeAwareBuilder<?>> {

  public T addAttribute(@Nonnull String name, @Nonnull String value) {
    if (!Strings.isNullOrEmpty(name) && !Strings.isNullOrEmpty(value)) {
      getAttributes().put(name, Attribute.Builder.newAttribute(name).withValue(value).build());
    }
    return getBuilder();
  }

  public T addAttribute(@Nonnull String name, @Nonnull String value, @Nullable Locale locale) {
    if(locale != null && "".equals(locale.toString())) {
      addAttribute(name, value);
    } else if (!Strings.isNullOrEmpty(name) && !Strings.isNullOrEmpty(value)) {
      getAttributes().put(name, Attribute.Builder.newAttribute(name).withValue(locale, value).build());
    }
    return getBuilder();
  }

  public T addAttribute(@Nonnull Attribute attribute) {
    if (attribute != null) {
      getAttributes().put(attribute.getName(), Attributes.copyOf(attribute));
    }
    return getBuilder();
  }

  public T addAttributes(@Nonnull Iterable<Attribute> attributes) {
    for(Attribute attribute : attributes) {
      addAttribute(attribute);
    }
    return getBuilder();
  }

  protected abstract ListMultimap<String, Attribute> getAttributes();

  protected abstract T getBuilder();

  public static ListMultimap<String, Attribute> overrideAttributes(Iterable<Attribute> existingAttributes,
      List<Attribute> overrideAttributes) {
    ListMultimap<String, Attribute> existingAttributesMultimap = LinkedListMultimap.create();
    for(Attribute attribute : existingAttributes) {
      existingAttributesMultimap.put(attribute.getName(), attribute);
    }
    return overrideAttributes(existingAttributesMultimap, overrideAttributes);
  }

  public static ListMultimap<String, Attribute> overrideAttributes(ListMultimap<String, Attribute> existingAttributes,
      Iterable<Attribute> overrideAttributes) {
    for(Attribute attribute : overrideAttributes) {
      overrideAttribute(existingAttributes, attribute);
    }
    return existingAttributes;
  }

  private static void overrideAttribute(ListMultimap<String, Attribute> attrs, Attribute attribute) {
    if(!attrs.containsEntry(attribute.getName(), attribute)) {
      if(attrs.containsKey(attribute.getName())) {
        if(attribute.isLocalised()) {
          removeLocalisedAttribute(attrs, attribute);
        } else {
          attrs.get(attribute.getName()).remove(0);
        }
      }
      attrs.put(attribute.getName(), attribute);
    }
  }

  private static void removeLocalisedAttribute(ListMultimap<String, Attribute> attrs, final Attribute attribute) {
    try {
      Attribute attributeToRemove = Iterables.find(attrs.get(attribute.getName()), new Predicate<Attribute>() {
        @Override
        public boolean apply(Attribute input) {
          return input != null //
              && attribute.getName().equals(input.getName()) //
              && (!attribute.isLocalised() && !input.isLocalised() ||
              attribute.isLocalised() && input.isLocalised() && attribute.getLocale().equals(input.getLocale()));
        }
      });
      attrs.remove(attributeToRemove.getName(), attributeToRemove);
    } catch(NoSuchElementException e) {
      // Nothing to remove.
    }
  }
}
