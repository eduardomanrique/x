package br.com.jesm.x;

import java.util.Date;

import br.com.jesm.x.dao.XDAO;
import br.com.jesm.x.model.internal.XScheduledExecution;

public class XScheduleService {

	XDAO<XScheduledExecution> dao = new XDAO<XScheduledExecution>(XScheduledExecution.class);
	
	public void executeOnDate(String methodName, Date date){
		XScheduledExecution exec = new XScheduledExecution();
		exec.setExecuted(false);
		exec.setExecutionDate(date);
		exec.setScheduleName(methodName);
		dao.insert(exec);
	}
}
