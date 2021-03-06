package sushi.query.bpmn;

import java.util.Arrays;
import java.util.List;

import sushi.bpmn.element.AbstractBPMNElement;
import sushi.bpmn.monitoringpoint.MonitoringPoint;
import sushi.bpmn.monitoringpoint.MonitoringPointStateTransition;
import sushi.query.PatternQueryType;
import sushi.query.SushiPatternQuery;
import sushi.query.SushiQueryTypeEnum;


/**
 * This query factory creates queries for concrete BPMN elements, 
 * which represents the monitorable state transitions of the current element.
 * @author micha
 */
public class StateTransitionQueryFactory extends AbstractPatternQueryFactory {

	public StateTransitionQueryFactory(PatternQueryGenerator patternQueryGenerator) {
		super(patternQueryGenerator);
	}

	/**
	 * This method should generate a query for a single BPMN element, this query makes it possible to monitor the life cycle of a BPMN element.
	 * It is possible to observe the single state transitions of an activity when it is executed with multiple monitoring points.
	 * @param element
	 * @param catchingMonitorableElement
	 * @param resendElement 
	 * @return
	 */
	@Override
	protected SushiPatternQuery generateQuery(AbstractBPMNElement element, AbstractBPMNElement catchingMonitorableElement, SushiPatternQuery parentQuery) {
		SushiPatternQuery query = null;
		String queryString = generateQueryString(element, catchingMonitorableElement);
		//Falls eine PatternQuery für dieses Element schon angelegt ist, dann wird die Query nur upgedated
		if(patternQueryGenerator.getQueryForElement(element) != null){
			query = patternQueryGenerator.getQueryForElement(element);
			addQueryRelationship(parentQuery, query);
			
			updateQuery(query);
			
			System.out.println(query.getTitle() + ": " + queryString);
			
			if(query != null && query.getListener() != null){
				query.getListener().setCatchingElement(catchingMonitorableElement);
			}
		} else {
			query = new SushiPatternQuery(generateQueryName("StateTransition"), queryString, SushiQueryTypeEnum.LIVE, PatternQueryType.STATETRANSITION, Arrays.asList(element));
			addQueryRelationship(parentQuery, query);
			
			registerQuery(query);
			
			System.out.println(query.getTitle() + ": " + queryString);
			
			if(query != null && query.getListener() != null){
				query.getListener().setCatchingElement(catchingMonitorableElement);
			}
		}
		
		return query;
	}

	/**
	 * @param element
	 * @param catchingMonitorableElement
	 * @return
	 */
	private String generateQueryString(AbstractBPMNElement element, AbstractBPMNElement catchingMonitorableElement) {
		//Nicht alle Monitoring Points eines Elements müssen vorhanden sein
		//Genereller Query-Ablauf: (Enable->Begin->Terminate) OR Skip
		MonitoringPoint enableMonitoringPoint = element.getMonitoringPointByStateTransitionType(MonitoringPointStateTransition.enable);
		MonitoringPoint beginMonitoringPoint = element.getMonitoringPointByStateTransitionType(MonitoringPointStateTransition.begin);
		MonitoringPoint terminateMonitoringPoint = element.getMonitoringPointByStateTransitionType(MonitoringPointStateTransition.terminate);
		MonitoringPoint skipMonitoringPoint = element.getMonitoringPointByStateTransitionType(MonitoringPointStateTransition.skip);
		
		List<MonitoringPoint> regularMonitoringPoints = Arrays.asList(enableMonitoringPoint, beginMonitoringPoint, terminateMonitoringPoint);
		
		int elementsWithMonitoringPoints = 0;
		StringBuilder sequencePatternQueryString = new StringBuilder();
		sequencePatternQueryString.append("SELECT * FROM PATTERN [(");
		
		for(MonitoringPoint monitoringPoint : regularMonitoringPoints){
			if(monitoringPoint != null && monitoringPoint.getEventType() != null){
				if(elementsWithMonitoringPoints > 0){
					sequencePatternQueryString.append(" " + EsperPatternOperators.SEQUENCE.operator + " ");
				}
				sequencePatternQueryString.append("EVERY S" + elementsWithMonitoringPoints + "=");
				sequencePatternQueryString.append(monitoringPoint.getEventType().getTypeName());
				elementsWithMonitoringPoints++;
			}
		}
		
		sequencePatternQueryString.append(") ");
		
		boolean elementHasSkipMonitoringPoint = skipMonitoringPoint != null && skipMonitoringPoint.getEventType() != null;
		
		if(elementHasSkipMonitoringPoint){
			if(elementsWithMonitoringPoints > 0){
				sequencePatternQueryString.append(" " + EsperPatternOperators.XOR.operator + " ");
			}
			sequencePatternQueryString.append("EVERY S" + elementsWithMonitoringPoints + "=");
			sequencePatternQueryString.append(skipMonitoringPoint.getEventType().getTypeName());
			elementsWithMonitoringPoints++;
		}
		
		if(catchingMonitorableElement == null){
			sequencePatternQueryString.append("]");
		} else {
			sequencePatternQueryString.append(" " + EsperPatternOperators.XOR.operator + " EVERY C1=");
			sequencePatternQueryString.append(catchingMonitorableElement.getMonitoringPoints().get(0).getEventType().getTypeName());
			sequencePatternQueryString.append("]");
		}
		
		if(!elementHasSkipMonitoringPoint){
			//gleiche ProcessInstanceID-Bedingung anhängen, falls Skip-Monitoring-Point nicht hinzugefügt, 
			//bei OR-Operator in Pattern ist die Bedingung nicht möglich
			sequencePatternQueryString.append(" WHERE sushi.esper.SushiUtils.isIntersectionNotEmpty({");
			for(int j = 0; j < elementsWithMonitoringPoints; j++){
				if(j == elementsWithMonitoringPoints - 1){ //letztes Element --> kein Komma
					sequencePatternQueryString.append("S" + j + ".ProcessInstances");
				} else {
					sequencePatternQueryString.append("S" + j + ".ProcessInstances,");
				}
			}
			sequencePatternQueryString.append("})");
		}
		
		String queryString = sequencePatternQueryString.toString();
		return queryString;
	}
	
}
