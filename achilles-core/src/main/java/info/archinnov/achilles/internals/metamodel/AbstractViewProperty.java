/*
 * Copyright (C) 2012-2016 DuyHai DOAN
 *
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

package info.archinnov.achilles.internals.metamodel;

import static info.archinnov.achilles.internals.schema.SchemaValidator.validateColumns;
import static info.archinnov.achilles.internals.statements.PreparedStatementGenerator.generateStaticDeleteQueries;
import static info.archinnov.achilles.internals.statements.PreparedStatementGenerator.generateStaticInsertQueries;
import static info.archinnov.achilles.internals.statements.PreparedStatementGenerator.generateStaticSelectQuery;
import static info.archinnov.achilles.validation.Validator.validateNotNull;
import static java.lang.String.format;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.MaterializedViewMetadata;
import com.datastax.driver.core.Session;

import info.archinnov.achilles.internals.cache.StatementsCache;
import info.archinnov.achilles.internals.context.ConfigurationContext;
import info.archinnov.achilles.internals.schema.SchemaContext;
import info.archinnov.achilles.internals.schema.SchemaCreator;
import info.archinnov.achilles.internals.utils.ListHelper;
import info.archinnov.achilles.type.interceptor.Event;
import info.archinnov.achilles.type.strategy.InsertStrategy;
import info.archinnov.achilles.type.tuples.Tuple3;
import info.archinnov.achilles.validation.Validator;

public abstract class AbstractViewProperty<T> extends AbstractEntityProperty<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractViewProperty.class);

    private final List<AbstractProperty<T, ?, ?>> EMPTY_LIST = Arrays.asList();

    private AbstractEntityProperty<?> baseClassProperty;

    public abstract Class<?> getBaseEntityClass();

    @Override
    public boolean isTable() {
        return  false;
    }

    @Override
    public boolean isView() {
        return true;
    }

    @Override
    public String generateSchema(SchemaContext context) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(format("Generating DDL script for entity of type %s",
                    entityClass.getCanonicalName()));
        }
        StringJoiner joiner = new StringJoiner("\n\n");
        SchemaCreator.generateView(context, this)
                .forEach(joiner::add);
        return joiner.toString();
    }

    @Override
    public void validateSchema(ConfigurationContext configContext) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(format("Validating schema for entity of type %s",
                    entityClass.getCanonicalName()));
        }
        final String keyspace = getKeyspace().orElse(null);

        Validator.validateNotBlank(keyspace,
                "Current keyspace not provided neither in configuration nor on entity '%s' annotation", entityClass.getCanonicalName());

        final KeyspaceMetadata keyspaceMetadata = configContext
                .getSession()
                .getCluster()
                .getMetadata()
                .getKeyspace(keyspace);

        validateNotNull(keyspaceMetadata,"The keyspace {} defined on entity {} does not exist in Cassandra",
                keyspace, entityClass.getCanonicalName());

        final String tableName = getTableOrViewName();

        final MaterializedViewMetadata viewMetadata = keyspaceMetadata
                .getMaterializedView(tableName);

        validateNotNull(viewMetadata,"The view {} defined on entity {} does not exist in Cassandra",
                tableName, entityClass.getCanonicalName());

        validateColumns(viewMetadata, partitionKeys, entityClass);
        validateColumns(viewMetadata, clusteringColumns, entityClass);
        validateColumns(viewMetadata, normalColumns, entityClass);
    }

    @Override
    public void prepareStaticStatements(Session session, StatementsCache cache) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(format("Preparing static statements for entity of type %s",
                    entityClass.getCanonicalName()));
        }
        generateStaticSelectQuery(session, cache, this);
    }

    protected List<AbstractProperty<T, ?, ?>> getAllColumns() {
        return ListHelper.appendAll(partitionKeys,
                clusteringColumns, normalColumns);
    }

    protected List<AbstractProperty<T, ?, ?>> getAllColumnsWithComputed() {
        return ListHelper.appendAll(partitionKeys,
                clusteringColumns, normalColumns, computedColumns);
    }

    @Override
    protected EntityType getType() {
        return EntityType.VIEW;
    }

    @Override
    public void inject(Tuple3<ConsistencyLevel, ConsistencyLevel, ConsistencyLevel> consistencyLevels) {
        this.readConsistencyLevel = consistencyLevels._1();
    }

    @Override
    public void inject(InsertStrategy insertStrategy) {
        // No Op
    }

    @Override
    public void triggerInterceptorsForEvent(Event event, T instance) {
        if (event != Event.POST_LOAD) {
            throw new RuntimeException("Cannot execute mutation for the materialized view " + getDerivedTableOrViewName());
        }
        super.triggerInterceptorsForEvent(event, instance);
    }

    @Override
    public InsertStrategy insertStrategy() {
        throw new RuntimeException("Cannot execute mutation for the materialized view " + getDerivedTableOrViewName());
    }

    @Override
    public ConsistencyLevel writeConsistency(Optional<ConsistencyLevel> runtimeConsistency) {
        throw new RuntimeException("Cannot execute mutation for the materialized view " + getDerivedTableOrViewName());
    }

    @Override
    public ConsistencyLevel serialConsistency(Optional<ConsistencyLevel> runtimeConsistency) {
        throw new RuntimeException("Cannot execute mutation for the materialized view " + getDerivedTableOrViewName());
    }

    @Override
    protected boolean isCounterTable() {
        return false;
    }

    @Override
    protected Optional<ConsistencyLevel> getStaticWriteConsistency() {
        return Optional.empty();
    }

    @Override
    protected Optional<ConsistencyLevel> getStaticSerialConsistency() {
        return Optional.empty();
    }

    @Override
    protected Optional<Integer> getStaticTTL() {
        return Optional.empty();
    }

    @Override
    protected Optional<InsertStrategy> getStaticInsertStrategy() {
        return Optional.empty();
    }

    @Override
    protected List<AbstractProperty<T, ?, ?>> getStaticColumns() {
        return EMPTY_LIST;
    }

    @Override
    protected List<AbstractProperty<T, ?, ?>> getCounterColumns() {
        return EMPTY_LIST;
    }

    public AbstractEntityProperty<?> getBaseClassProperty() {
        return baseClassProperty;
    }

    public void setBaseClassProperty(AbstractEntityProperty<?> baseClassProperty) {
        this.baseClassProperty = baseClassProperty;
    }
}
