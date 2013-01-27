package parser.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import mapping.entity.TweetMultiKey;
import fr.doan.achilles.annotations.ColumnFamily;
import fr.doan.achilles.entity.type.WideMap;

/**
 * MultiKeyWideRowBean
 * 
 * @author DuyHai DOAN
 * 
 */
@Entity
@ColumnFamily
public class MultiKeyColumnFamilyBean implements Serializable
{

	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column
	private WideMap<TweetMultiKey, String> values;

	public Long getId()
	{
		return id;
	}

	public void setId(Long id)
	{
		this.id = id;
	}

	public WideMap<TweetMultiKey, String> getValues()
	{
		return values;
	}

	public void setValues(WideMap<TweetMultiKey, String> values)
	{
		this.values = values;
	}
}