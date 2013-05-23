package info.archinnov.achilles.entity.manager;

import info.archinnov.achilles.configuration.AchillesArgumentExtractor;
import info.archinnov.achilles.consistency.AchillesConsistencyLevelPolicy;
import info.archinnov.achilles.context.AchillesConfigurationContext;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.entity.parsing.AchillesEntityExplorer;
import info.archinnov.achilles.entity.parsing.AchillesEntityParser;
import info.archinnov.achilles.entity.parsing.context.AchillesEntityParsingContext;
import info.archinnov.achilles.entity.parsing.validator.AchillesEntityParsingValidator;
import info.archinnov.achilles.exception.AchillesException;
import info.archinnov.achilles.table.AchillesTableCreator;
import info.archinnov.achilles.validation.Validator;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Cache;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.metamodel.Metamodel;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AchillesEntityManagerFactory
 * 
 * @author DuyHai DOAN
 * 
 */
public abstract class AchillesEntityManagerFactory implements EntityManagerFactory
{
	private static final Logger log = LoggerFactory.getLogger(AchillesEntityManagerFactory.class);

	protected Map<Class<?>, EntityMeta> entityMetaMap = new HashMap<Class<?>, EntityMeta>();
	protected AchillesTableCreator tableCreator;
	protected AchillesConfigurationContext configContext;
	protected List<String> entityPackages;

	private AchillesEntityParser achillesEntityParser = new AchillesEntityParser();
	private AchillesEntityExplorer achillesEntityExplorer = new AchillesEntityExplorer();
	private AchillesEntityParsingValidator validator = new AchillesEntityParsingValidator();

	protected AchillesEntityManagerFactory(Map<String, Object> configurationMap,
			AchillesArgumentExtractor argumentExtractor)
	{
		Validator.validateNotNull(configurationMap,
				"Configuration map for AchillesEntityManagerFactory should not be null");
		Validator.validateNotEmpty(configurationMap,
				"Configuration map for AchillesEntityManagerFactory should not be empty");

		entityPackages = argumentExtractor.initEntityPackages(configurationMap);
		configContext = parseConfiguration(configurationMap, argumentExtractor);
	}

	protected boolean bootstrap()
	{
		log.info("Bootstraping Achilles Thrift-based EntityManagerFactory ");

		boolean hasSimpleCounter = false;
		try
		{
			hasSimpleCounter = discoverEntities();
		}
		catch (Exception e)
		{
			throw new AchillesException("Exception during entity parsing : " + e.getMessage(), e);
		}

		tableCreator.validateOrCreateColumnFamilies(entityMetaMap, configContext, hasSimpleCounter);

		return hasSimpleCounter;
	}

	protected boolean discoverEntities() throws ClassNotFoundException, IOException
	{
		log.info("Start discovery of entities, searching in packages '{}'",
				StringUtils.join(entityPackages, ","));
		Map<PropertyMeta<?, ?>, Class<?>> joinPropertyMetaToBeFilled = new HashMap<PropertyMeta<?, ?>, Class<?>>();

		List<Class<?>> entities = achillesEntityExplorer.discoverEntities(entityPackages);
		validator.validateAtLeastOneEntity(entities, entityPackages);
		boolean hasSimpleCounter = false;
		for (Class<?> entityClass : entities)
		{
			AchillesEntityParsingContext context = new AchillesEntityParsingContext(//
					joinPropertyMetaToBeFilled, //
					configContext, entityClass);

			EntityMeta entityMeta = achillesEntityParser.parseEntity(context);
			entityMetaMap.put(entityClass, entityMeta);
			hasSimpleCounter = context.getHasSimpleCounter() || hasSimpleCounter;
		}

		achillesEntityParser.fillJoinEntityMeta(new AchillesEntityParsingContext( //
				joinPropertyMetaToBeFilled, //
				configContext), entityMetaMap);

		return hasSimpleCounter;
	}

	protected abstract AchillesConsistencyLevelPolicy initConsistencyLevelPolicy(
			Map<String, Object> configurationMap, AchillesArgumentExtractor argumentExtractor);

	protected AchillesConfigurationContext parseConfiguration(Map<String, Object> configurationMap,
			AchillesArgumentExtractor argumentExtractor)
	{
		AchillesConfigurationContext configContext = new AchillesConfigurationContext();
		configContext.setEnsureJoinConsistency(argumentExtractor
				.ensureConsistencyOnJoin(configurationMap));
		configContext.setForceColumnFamilyCreation(argumentExtractor
				.initForceCFCreation(configurationMap));
		configContext.setConsistencyPolicy(initConsistencyLevelPolicy(configurationMap,
				argumentExtractor));
		configContext.setObjectMapperFactory(argumentExtractor
				.initObjectMapperFactory(configurationMap));

		return configContext;
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public CriteriaBuilder getCriteriaBuilder()
	{
		throw new UnsupportedOperationException("This operation is not supported by Achilles");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Metamodel getMetamodel()
	{
		throw new UnsupportedOperationException("This operation is not supported by Achilles");
	}

	@Override
	public Map<String, Object> getProperties()
	{
		// TODO Implement
		return new HashMap<String, Object>();
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Cache getCache()
	{
		throw new UnsupportedOperationException("This operation is not supported by Achilles");
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public void close()
	{
		throw new UnsupportedOperationException("This operation is not supported by Achilles");
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public boolean isOpen()
	{
		throw new UnsupportedOperationException("This operation is not supported by Achilles");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public PersistenceUnitUtil getPersistenceUnitUtil()
	{
		// TODO provide a PersistenceUnitUtil, easy
		throw new UnsupportedOperationException("This operation is not supported by Achilles");
	}

	protected void setTableCreator(AchillesTableCreator tableCreator)
	{
		this.tableCreator = tableCreator;
	}

	protected void setEntityPackages(List<String> entityPackages)
	{
		this.entityPackages = entityPackages;
	}

	protected void setEntityParser(AchillesEntityParser achillesEntityParser)
	{
		this.achillesEntityParser = achillesEntityParser;
	}

	protected void setEntityExplorer(AchillesEntityExplorer achillesEntityExplorer)
	{
		this.achillesEntityExplorer = achillesEntityExplorer;
	}

	protected void setValidator(AchillesEntityParsingValidator validator)
	{
		this.validator = validator;
	}

	protected void setEntityMetaMap(Map<Class<?>, EntityMeta> entityMetaMap)
	{
		this.entityMetaMap = entityMetaMap;
	}

	protected void setConfigContext(AchillesConfigurationContext configContext)
	{
		this.configContext = configContext;
	}

}