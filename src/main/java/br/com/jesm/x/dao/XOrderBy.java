package br.com.jesm.x.dao;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;

public class XOrderBy {

	private boolean asc;
	private String[] properties;

	public XOrderBy(String... properties) {
		this(true, properties);
	}

	public XOrderBy(boolean asc, String... properties) {
		this.asc = asc;
		this.properties = properties;
	}

	protected void configCriteria(DetachedCriteria criteria) {
		for (String property : properties) {
			if (asc) {
				criteria.addOrder(Order.asc(property));
			} else {
				criteria.addOrder(Order.desc(property));
			}
		}
	}
}
