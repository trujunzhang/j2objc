/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.cyclefinder;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

/**
 * Manages the set of whitelist entries and parses whitelist files.
 *
 * @author Keith Stanger
 */
public class Whitelist {

  private Set<String> fields = Sets.newHashSet();
  private SetMultimap<String, String> fieldsWithTypes = HashMultimap.create();
  private Set<String> types = Sets.newHashSet();
  private Set<String> namespaces = Sets.newHashSet();
  private Set<String> outers = Sets.newHashSet();

  public boolean containsField(IVariableBinding field) {
    return fields.contains(fieldName(field));
  }

  public boolean hasWhitelistedTypesForField(IVariableBinding field) {
    return fieldsWithTypes.containsKey(fieldName(field));
  }

  public boolean isWhitelistedTypeForField(IVariableBinding field, ITypeBinding type) {
    return fieldsWithTypes.containsEntry(fieldName(field), typeName(type));
  }

  public boolean hasOuterForType(ITypeBinding type) {
    return outers.contains(typeName(type));
  }

  public boolean containsType(ITypeBinding type) {
    String typeName = typeName(type);
    if (types.contains(typeName)) {
      return true;
    }
    while (true) {
      if (namespaces.contains(typeName)) {
        return true;
      }
      int idx = typeName.lastIndexOf('.');
      if (idx < 0) {
        break;
      }
      typeName = typeName.substring(0, idx);
    }
    return false;
  }

  private static String fieldName(IVariableBinding field) {
    return typeName(field.getDeclaringClass()) + "." + field.getName();
  }

  private static String typeName(ITypeBinding type) {
    IMethodBinding declaringMethod = type.getDeclaringMethod();
    if (declaringMethod != null) {
      return typeName(declaringMethod.getDeclaringClass()) + "." + declaringMethod.getName()
          + "." + (type.isAnonymous() ? "$" : type.getName());
    }
    return type.getErasure().getQualifiedName();
  }

  private static final Splitter ENTRY_SPLITTER =
      Splitter.on(CharMatcher.WHITESPACE).trimResults().omitEmptyStrings();

  public void addEntry(String entry) {
    String[] tokens = Iterables.toArray(ENTRY_SPLITTER.split(entry), String.class);
    if (tokens.length < 2) {
      badEntry(entry);
    }

    String entryType = tokens[0].toLowerCase();
    if (entryType.equals("field")) {
      if (tokens.length == 2) {
        fields.add(tokens[1]);
      } else if (tokens.length == 3) {
        fieldsWithTypes.put(tokens[1], tokens[2]);
      } else {
        badEntry(entry);
      }
    } else if (entryType.equals("type") && tokens.length == 2) {
      types.add(tokens[1]);
    } else if (entryType.equals("namespace") && tokens.length == 2) {
      namespaces.add(tokens[1]);
    } else if (entryType.equals("outer") && tokens.length == 2) {
      outers.add(tokens[1]);
    } else {
      badEntry(entry);
    }
  }

  private void badEntry(String entry) {
    throw new IllegalArgumentException("Invalid whitelist entry: " + entry);
  }

  public void addFile(String file) throws IOException {
    BufferedReader in = new BufferedReader(new FileReader(new File(file)));
    try {
      for (String line = in.readLine(); line != null; line = in.readLine()) {
        String entry = line.split("#", 2)[0].trim();
        if (!Strings.isNullOrEmpty(entry)) {
          addEntry(entry);
        }
      }
    } finally {
      in.close();
    }
  }

  public static Whitelist createFromFiles(Iterable<String> files) throws IOException {
    Whitelist whitelist = new Whitelist();
    for (String file : files) {
      whitelist.addFile(file);
    }
    return whitelist;
  }
}
