package org.obiba.meta.js;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.Test;
import org.obiba.meta.Collection;
import org.obiba.meta.MetaEngine;
import org.obiba.meta.NoSuchCollectionException;
import org.obiba.meta.Value;
import org.obiba.meta.ValueSet;
import org.obiba.meta.Variable;
import org.obiba.meta.VariableEntity;
import org.obiba.meta.VariableValueSource;
import org.obiba.meta.support.ValueSetBean;
import org.obiba.meta.support.VariableEntityBean;
import org.obiba.meta.type.TextType;

public class JavascriptVariableValueSourceTest extends AbstractJsTest {

  VariableValueSource mockSource = EasyMock.createMock(VariableValueSource.class);

  Collection mockCollection = EasyMock.createMock(Collection.class);

  protected MetaEngine newEngine() {

    return new MetaEngine() {
      public VariableValueSource lookupVariable(String entityType, String name) throws org.obiba.meta.NoSuchCollectionException, org.obiba.meta.NoSuchVariableException {
        return mockSource;
      };

      @Override
      public Collection lookupCollection(String name) throws NoSuchCollectionException {
        return mockCollection;
      }
    };
  }

  @Test
  public void testVariableLookup() {
    // Build the javascript variable that returns AnotherVariable's value
    Variable.Builder builder = Variable.Builder.newVariable("my-collection", "JavascriptVariable", TextType.get(), "Participant");
    Variable variable = builder.extend(JavascriptVariableBuilder.class).setScript("$('AnotherVariable')").build();
    JavascriptVariableValueSource source = new JavascriptVariableValueSource(variable);

    source.initialise();

    // Create the VariableValueSource for AnotherVariable
    Variable anotherVariable = Variable.Builder.newVariable("my-collection", "AnotherVariable", TextType.get(), "Participant").build();
    EasyMock.expect(mockSource.getVariable()).andReturn(anotherVariable).anyTimes();
    EasyMock.expect(mockSource.getValue((ValueSet) EasyMock.anyObject())).andReturn(TextType.get().valueOf("The Value")).anyTimes();

    ValueSet valueSet = new ValueSetBean(mockCollection, new VariableEntityBean("Participant", "1234"));
    EasyMock.expect(mockCollection.getName()).andReturn("my-collection").anyTimes();
    EasyMock.expect(mockCollection.loadValueSet((VariableEntity) EasyMock.anyObject())).andReturn(valueSet).anyTimes();

    EasyMock.replay(mockSource, mockCollection);
    Value value = source.getValue(valueSet);

    Assert.assertNotNull(value);
    Assert.assertFalse(value.isNull());
    Assert.assertEquals("The Value", value.toString());
  }

}
