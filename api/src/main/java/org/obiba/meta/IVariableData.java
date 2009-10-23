/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.meta;

/**
 *
 */
public interface IVariableData {

  public IVariable getVariable();

  public IValueSetReference getReference();

  public Value getValue();

  public ValueType getValueType();

}
