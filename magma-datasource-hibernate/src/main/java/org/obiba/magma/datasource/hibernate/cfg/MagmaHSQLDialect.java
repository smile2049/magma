/*
 * Copyright (c) 2019 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.datasource.hibernate.cfg;

import java.sql.Types;

import org.hibernate.dialect.HSQLDialect;

/**
 * Overrides the HSQLDialect to force the use of longvarchar for clobs
 */
public class MagmaHSQLDialect extends HSQLDialect {

  public MagmaHSQLDialect() {
    // Force the use of longvarchar and longvarbinary for clobs/blobs
    registerColumnType(Types.BLOB, "longvarbinary");
    registerColumnType(Types.CLOB, "longvarchar");
  }
}
