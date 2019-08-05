/*
 * Copyright 2019 Jonathan West
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
*/

package com.githubapimirror.shared;

/**
 * A GitHub repository may have either an organization as a parent
 * (github.com/golang/repo), or a user repository as a parent
 * (github.com/username/repo). This class may represents either parent, which we
 * refer to as an 'Owner'.
 */
public class Owner {

	public static enum Type {
		ORG, USER
	}

	private final String name;
	private final Type type;

	private Owner(String name, Type type) {
		if (name == null) {
			throw new IllegalArgumentException();
		}
		if (name.contains(" ")) {
			throw new IllegalArgumentException();
		}

		this.name = name;
		this.type = type;
	}

	public String getOrgNameOrNull() {
		if (type == Type.ORG) {
			return name;
		} else {
			return null;
		}
	}

	public String getUserNameOrNull() {
		if (type == Type.USER) {
			return name;
		} else {
			return null;
		}
	}

	public Type getType() {
		return type;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Owner)) {
			return false;
		}
		Owner other = (Owner) obj;

		return other.getType() == this.getType() && other.name.equals(this.name);

	}

	@Override
	public String toString() {
		return type.name() + " " + name;
	}

	// ------------------------------------------

	public static Owner org(String name) {
		return new Owner(name, Type.ORG);
	}

	public static Owner user(String name) {
		return new Owner(name, Type.USER);
	}

	public String getName() {
		return name;
	}
}
