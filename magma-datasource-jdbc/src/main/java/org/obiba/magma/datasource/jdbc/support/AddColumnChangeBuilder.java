package org.obiba.magma.datasource.jdbc.support;

import liquibase.change.AddColumnConfig;
import liquibase.change.core.AddColumnChange;

public class AddColumnChangeBuilder {

  private final AddColumnChange addColumnChange = new AddColumnChange();

  public static AddColumnChangeBuilder newBuilder() {
    return new AddColumnChangeBuilder();
  }

  public AddColumnChangeBuilder table(String tableName) {
    addColumnChange.setTableName(tableName);

    return this;
  }

  public AddColumnChangeBuilder column(String columnName, String dataType) {
    AddColumnConfig ac = new AddColumnConfig();
    ac.setName(columnName);
    ac.setType(dataType);

    addColumnChange.addColumn(ac);

    return this;
  }

  public AddColumnChange build() {
    return addColumnChange;
  }
}
