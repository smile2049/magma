/*
 * Copyright (c) 2019 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.obiba.magma.datasource.hibernate.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.obiba.core.domain.AbstractEntity;

/**
 * Persistent entities that extend this class are provided with read-only 'created' and 'updated' timestamp fields.
 * These fields indicate the time that the entity was created and the last time the entity was updated. The subclass will also receive the
 * generated 'id' field from the parent {@link AbstractEntity} class.
 */
@MappedSuperclass
public abstract class AbstractTimestampedEntity extends AbstractEntity implements Timestamped {

  private static final long serialVersionUID = 9121648752351099987L;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(insertable = true, updatable = false, nullable = false)
  @SuppressWarnings("FieldMayBeFinal")
  private Date created = new Date();

  @Column(nullable = false)
  @SuppressWarnings("UnusedDeclaration")
  private Date updated = new Date();

  @Override
  public Date getCreated() {
    return new Date(created.getTime());
  }

  @Override
  public Date getUpdated() {
    return new Date(updated.getTime());
  }

  public void setUpdated(Date updated) {
    this.updated = updated;
  }
}
