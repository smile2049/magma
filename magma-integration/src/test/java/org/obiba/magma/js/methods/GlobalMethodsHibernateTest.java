/*
 * Copyright (c) 2019 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.js.methods;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obiba.magma.*;
import org.obiba.magma.datasource.generated.GeneratedValueTable;
import org.obiba.magma.datasource.hibernate.HibernateDatasource;
import org.obiba.magma.js.AbstractJsTest;
import org.obiba.magma.js.views.VariablesClause;
import org.obiba.magma.support.DatasourceCopier;
import org.obiba.magma.support.Initialisables;
import org.obiba.magma.type.IntegerType;
import org.obiba.magma.views.DefaultViewManagerImpl;
import org.obiba.magma.views.MemoryViewPersistenceStrategy;
import org.obiba.magma.views.View;
import org.obiba.magma.views.ViewManager;
import org.obiba.magma.xstream.MagmaXStreamExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.obiba.magma.Variable.Builder.newVariable;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/test-context.xml")
@SuppressWarnings({"OverlyLongMethod", "PMD.NcssMethodCount"})
public class GlobalMethodsHibernateTest extends AbstractJsTest {

  private static final Logger log = LoggerFactory.getLogger(GlobalMethodsHibernateTest.class);

  private static final String DATASOURCE = "ds";

  private static final String TABLE = "table";

  private static final String VARIABLE_AGE = "age";

  private static final String VARIABLE_WEIGHT = "weight";

  private static final String VARIABLE_HEIGHT = "height";

  private ViewManager viewManager;

  @Autowired
  private SessionFactory sessionFactory;

  @Autowired
  private TransactionTemplate transactionTemplate;

  public GlobalMethodsHibernateTest() {
    System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
  }

  @Before
  @Override
  public void before() {
    super.before();
    viewManager = new DefaultViewManagerImpl(new MemoryViewPersistenceStrategy());
    cleanlyRemoveHibernateDatasource(true);
  }

  @After
  @Override
  public void after() {
    super.after();
    cleanlyRemoveHibernateDatasource(true);
  }

  @Override
  protected MagmaEngine newEngine() {
    MagmaEngine magmaEngine = super.newEngine();
    magmaEngine.extend(new MagmaXStreamExtension());
    return magmaEngine;
  }

  @Override
  protected void shutdownEngine() {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      @Override
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        MagmaEngine.get().shutdown();
      }
    });
  }

  private void cleanlyRemoveHibernateDatasource(final boolean drop) {

    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      @Override
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        try {
          if (drop) {
            Datasource datasource = new HibernateDatasource(DATASOURCE, sessionFactory);
            Initialisables.initialise(datasource);
            MagmaEngine.get().addDatasource(datasource);
            datasource.drop();
          }
          MagmaEngine.get().removeDatasource(MagmaEngine.get().getDatasource(DATASOURCE));
        } catch (Throwable e) {
          log.warn("Cannot remove datasource", e);
        }
      }
    });
  }

  private Datasource getTestDatasource() {
    HibernateDatasource datasource = new HibernateDatasource(DATASOURCE, sessionFactory);

    List<Variable> variables = Lists.newArrayList( //
        newVariable(VARIABLE_AGE, IntegerType.get(), AbstractJsTest.PARTICIPANT).build(), //
        newVariable(VARIABLE_WEIGHT, IntegerType.get(), AbstractJsTest.PARTICIPANT).unit("kg").build(), //
        newVariable(VARIABLE_HEIGHT, IntegerType.get(), AbstractJsTest.PARTICIPANT).unit("cm").build());
    ValueTable generatedValueTable = new GeneratedValueTable(datasource, variables, 150);

    Datasource viewAwareDatasource = viewManager.decorate(datasource);
    MagmaEngine.get().addDatasource(viewAwareDatasource);

    try {
      DatasourceCopier.Builder.newCopier().build().copy(generatedValueTable, TABLE, datasource);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return viewAwareDatasource;
  }

  @Test
  @Ignore
  public void test_$this_vector_performance() {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      @Override
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Datasource datasource = getTestDatasource();
        ValueTable table = datasource.getValueTable(TABLE);

        Collection<Variable> variables = Lists.newArrayList( //
            AbstractJsTest.createIntVariable("A", "$('ds.table:weight')"), //
            AbstractJsTest.createIntVariable("B", "$this('A')"), //
            AbstractJsTest.createIntVariable("C", "$this('B')"));

        View viewTemplate = View.Builder.newView("view", table).list(new VariablesClause()).build();
        try (ValueTableWriter.VariableWriter variableWriter = viewTemplate.getListClause().createWriter()) {
          for (Variable variable : variables) {
            variableWriter.writeVariable(variable);
          }
        }
        viewManager.addView(DATASOURCE, viewTemplate, null, null);

        View view = viewManager.getView(DATASOURCE, "view");
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<VariableEntity> entities = view.getVariableEntities();
        log.info("Load {} entities in {}", entities.size(), stopwatch);
        stopwatch.reset().start();
        VariableValueSource variableValueSource = view.getVariableValueSource("C");
        VectorSource vectorSource = variableValueSource.asVectorSource();
        Iterable<Value> values = vectorSource.getValues(entities);
        for (Value viewValue : values) {
          viewValue.getValue();
        }
        log.info("Load vector in {}", stopwatch.stop());
        assertThat(stopwatch.elapsed(TimeUnit.SECONDS)).isLessThan(1);
      }
    });
  }

}
