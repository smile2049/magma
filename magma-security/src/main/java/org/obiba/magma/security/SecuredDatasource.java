/*
 * Copyright (c) 2019 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.security;

import org.obiba.magma.Datasource;
import org.obiba.magma.MagmaRuntimeException;
import org.obiba.magma.NoSuchValueTableException;
import org.obiba.magma.ValueTable;
import org.obiba.magma.security.permissions.Permissions.DatasourcePermissionBuilder;
import org.obiba.magma.support.AbstractDatasourceWrapper;

import java.util.Set;
import java.util.stream.Collectors;

public class SecuredDatasource extends AbstractDatasourceWrapper {

  private final Authorizer authz;

  public SecuredDatasource(Authorizer authorizer, Datasource datasource) {
    super(datasource);
    if (authorizer == null) throw new IllegalArgumentException("authorizer cannot be null");
    authz = authorizer;
  }

  @Override
  public ValueTable getValueTable(String name) throws NoSuchValueTableException {
    ValueTable table = getWrappedDatasource().getValueTable(name);
    if (table != null && !authzReadTable(name)) throw new NoSuchValueTableException(getName(), name);
    return new SecuredValueTable(authz, this, table);
  }

  @Override
  public Set<ValueTable> getValueTables() {
    return getWrappedDatasource().getValueTables().stream()
        .filter(builder().tables().read().asPredicate(authz))
        .map(table -> new SecuredValueTable(authz, SecuredDatasource.this, table))
        .collect(Collectors.toSet());
  }

  @Override
  public boolean hasValueTable(String name) {
    return getWrappedDatasource().hasValueTable(name) && authzReadTable(name);
  }

  @Override
  public boolean canDropTable(String name) {
    return getWrappedDatasource().canDropTable(name) && authzDropTable(name);
  }

  @Override
  public void dropTable(String name) {
    if (hasValueTable(name)) {
      if (!authzDropTable(name)) {
        throw new MagmaRuntimeException("not authorized to drop table " + getName() + "." + name);
      }
      getWrappedDatasource().dropTable(name);
    }
  }

  protected boolean authzReadTable(String name) {
    return authz.isPermitted(builder().table(name).read().build());
  }

  protected boolean authzDropTable(String name) {
    return authz.isPermitted(builder().table(name).delete().build());
  }

  private DatasourcePermissionBuilder builder() {
    return DatasourcePermissionBuilder.forDatasource(getWrappedDatasource());
  }
}
