var classes = {};
var controllers = {};

function init(){
	X.debug("xstartup", "XObj INIT");
	var script = window.location.pathname.substring("%ctx%".length) + ".js";
	X.debug("xstartup", "XObj importing main script " + script);
	xremote.importScript(script, function(controller){
		X.debug("xstartup", "XObj Starting main controller");
		xcomponents.afterLoadController();
		setMainController(controller);
	});
	X.debug("xstartup", "XObj done");
}

function onController(c, callback) {
	var cid = c._x_getControllerId();
	controllers[cid] = c;
	xinputs.configEvents();
	xinputs.setContextOnInputs(cid);
	if (callback) {
		callback(c);
	}
}

function loadObj(objName) {
	xlog.debug("_x_load_obj", "Obj: " + objName)
	var inputArray = xdom.getElementsByAttribute('xvar', objName + '.', true);
	var rootArray = xdom.getElementsByAttribute('xvar', objName, false);
	inputArray = inputArray.concat(rootArray);
	for ( var i in inputArray) {
		if (inputArray[i].tagName.toLowerCase() != 'xobject' && inputArray[i] !== document.activeElement
				&& inputArray[i].getAttribute
				&& inputArray[i].getAttribute("xvar")
				&& !(inputArray[i].getAttribute("type") == "hidden")) {
			var value = null;
			try{
				value = evalOnContext(xobj.getElementCtx(inputArray[i]), inputArray[i].getAttribute("xvar"));
			}catch(e){}
					
			if (!value) {
				xinputs.setValueOnInput(inputArray[i], '');
			} else {
				var xtype = inputArray[i].getAttribute('xtype');
				if (xtype == 'ifloat'){
					xtype = parseInt(value) == value ? 'int' : 'float';
				}
				if (xtype == 'float') {
					value = xmask.getDefaultFormatter().format(value);
				} else if (xtype == 'int') {
					value = parseInt(value);
				} else if (xtype == 'date') {
					var dtFormat = inputArray[i].getAttribute("xdateformat") || '%defaultdateformat%';
					if(inputArray[i].getAttribute("xdatetype") == 'true'){
						value = value instanceof Date ? value : xmask.parseDate(value, dtFormat);
					}else{
						value = xmask.padR(xmask.getNumber(value, true), '0', 8);
						value = xmask.applyDateMask(value, dtFormat);
					}
				} else if (xtype == 'datetime') {
					value = xmask.padR(xmask.getNumber(value, true), '0', 12);
					value = value[0] + value[1] + '/' + value[2] + value[3]
							+ '/' + value[4] + value[5] + value[6] + value[7] 
							+ ' ' + value[8] + value[9] + ':' + value[10] + value[11];
				}
				if (xtype == 'autocomplete'){
					xvisual.getAutocomplete(inputArray[i]).setValue(value);
				}else{
					xinputs.setValueOnInput(inputArray[i], value);					
				}
			}
		}
	}
}

function buildObj(name) {
	var result = null;
	var valueArray = xdom.getElementsByAttribute('xvar', name + '.', true);
	var val = 0;
	var currentObject = null;
	var currentPropName = 0;

	for ( var i in valueArray) {
		if (result == null)
			result = {};
		var isRadio = valueArray[i].getAttribute
				&& valueArray[i].getAttribute("type") == 'radio';
		if (valueArray[i].getAttribute && valueArray[i].getAttribute("xvar")
				&& (!isRadio || valueArray[i].checked)) {
			currentObject = result;
			if (valueArray[i].getAttribute("xvar")[name.length] != '.') {
				continue;
			}
			var propName = valueArray[i].getAttribute("xvar").substring(
					name.length + 1);
			var splittedPropName = propName.split(".");
			var arrayIndex = 0;
			var isArray = false;
			for ( var j in splittedPropName) {
				var spPropName = splittedPropName[j];
				isArray = false;
				if (spPropName.indexOf("]") == spPropName.length - 1) {
					arrayIndex = parseInt(spPropName.substring(spPropName
							.indexOf("[") + 1, spPropName.length - 1));
					spPropName = spPropName.substring(0, spPropName
							.indexOf("["));
					isArray = true;
				}
				if (j == splittedPropName.length - 1) {
					currentPropName = spPropName;
				} else {
					if(X.isDevMode) {
						createProperty(currentObject, spPropName);						
					}
					if (isArray) {
						if (!currentObject[spPropName]) {
							currentObject[spPropName] = [];
						}
						if (!currentObject[spPropName][arrayIndex]) {
							currentObject[spPropName][arrayIndex] = {};
						}
					} else if (!currentObject[spPropName]) {
						currentObject[spPropName] = {};
					}
					if (isArray) {
						currentObject = currentObject[spPropName][arrayIndex];
					} else {
						currentObject = currentObject[spPropName];
					}
				}
			}
			val = xinputs.getValueFromInput(valueArray[i]);

			try {
				if(X.isDevMode) {
					createProperty(currentObject, currentPropName);						
				}
				if(valueArray[i].getAttribute("xisjson") == "true"){
					var _xtemp_var;
					eval('_xtemp_var=' + decodeURIComponent(val));
					val = _xtemp_var;
				}
				currentObject[currentPropName] = val;					
			} catch (e) {
			}
		}
	}
	return result;
}

function createProperty(obj, propertyName){
	var meta = obj._x_meta_object_properties;
	if(!meta){
		meta = {}
		obj._x_meta_object_properties = meta;
	}
	if(!meta[propertyName]){
		var _v = obj[propertyName];
		try{
			delete obj[propertyName];
			Object.defineProperty(obj, propertyName, {
				get : function() {
					xlog.debug("xobj_property", "get: " + propertyName + " on "+ xutil.stringify(this));
					return this["_x_value_on_property_" + propertyName];
				},
				set : function(v) {
					xlog.debug("xobj_property", "before set: " + propertyName + ", value: " + v + " on "+ xutil.stringify(this));
					this["_x_value_on_property_" + propertyName] = v;
					xlog.debug("xobj_property", "after set: " + propertyName + ", value: " + v + " on "+ xutil.stringify(this));
				}
			});
			meta[propertyName] = true;
		}catch(e){
			obj[propertyName] = _v;
		}
	}
}

function updateObject(obj) {
	var v;
	if (obj.getVar) {
		v = obj.getVar();
	} else {
		v = obj.getAttribute('xvar');
	}
	if (v) {
		xlog.debug("updateObject", "Input xvar: " + v + ", value: " + obj.value
				+ ", id: " + obj.id);
		var ctxId;
		if (obj.getCtxId) {
			ctxId = obj.getCtxId();
		} else {
			ctxId = getElementCtx(obj);
		}
		if (obj.getAttribute && obj.getAttribute("type") == 'checkbox') {
			xlog.debug("updateObject", "Input xvar: " + v
					+ ", checkbox checked: " + obj.checked);
			evalOnContext(ctxId, v + ' = ' + obj.checked);
		} else if (obj.getAttribute && obj.getAttribute("type") == 'radio') {
			var elarray = document.getElementsByName(obj.getAttribute("name"));
			xlog.debug("updateObject", "Input xvar: " + v + ", radio name: "
					+ obj.getAttribute("name") + ", len: " + elarray.length);
			var objVal = obj;
			for ( var i = 0; i < elarray.length; i++) {
				if (elarray[i].checked) {
					objVal = elarray[i];
					break;
				}
			}
			window['_x_temp_var_'] = xinputs.getValueFromInput(objVal);
			xlog.debug("updateObject", "Input xvar: " + v
					+ ", radio valFromInput: " + window['_x_temp_var_']);
			var lastDot = v.lastIndexOf(".");
			if(lastDot > 0){
				createProperty(evalOnContext(ctxId, v.substring(0, lastDot)), v.substring(lastDot + 1));
			}
			
			evalOnContext(ctxId, v + ' = _x_temp_var_');
			window['_x_temp_var_'] = null;
		} else {
			xlog.debug("updateObject", "Input xvar: " + v + ", normal input");

			var newVal;
			if (obj.getValue) {
				newVal = obj.getValue();
			} else {
				newVal = xinputs.getValueFromInput(obj);
			}
			xlog.debug("updateObject", "Input xvar: " + v + ", new value " + newVal);
			var array = xdom.getElementsByAttribute('xvar', v);
			for ( var i in array) {
				var item = array[i];
				if (item !== document.activeElement
						&& (!item.getAttribute || item.getAttribute("type") != "radio") && item.getAttribute("xtype") != 'autocomplete') {
					xinputs.setValueOnInput(item, newVal);
				}
			}
			window['_x_temp_var_'] = newVal;
			xlog.debug("updateObject", "Input xvar: " + v + ", valFromInput: "
					+ window['_x_temp_var_']);
			evalOnContext(ctxId, v + ' = _x_temp_var_');
			window['_x_temp_var_'] = null;
		}
		xlog.debug("updateObject", "Input xvar: " + v + ", value: " + obj.value
				+ ", END");
	}
	xlog.debug("updateObject", "end update input");
}

function existsObject(varName, ctxId) {
	try {
		var obj = evalOnContext(ctxId, varName);
		return obj != null;
	} catch (e) {
		return false;
	}
}

var grabValueFromPropertyOrInput = function(v, ctxId) {
	var finalVal = null;
	var inputs = xdom.getElementsByAttribute('xvar', v);
	xutil.each(inputs, function(input) {
		if (input.value != '') {
			finalVal = xinputs.getValueFromInput(input);
			return false;
		}
	});

	var array = v.split(".");
	var varName = array.shift();
	var c = controllers[ctxId];
	var obj = evalOnContext(c, varName);
	var lastArrayIndex = null;
	while (array.length > 0) {
		var property = array.shift();
		varName = varName + '.' + property;
		var objProperty = evalOnContext(c, varName);
		if (objProperty == null) {
			var isArray = property.indexOf('[') > 0;
			var indexComp = null
			if (isArray) {
				indexComp = parseInt(property.substring(
						property.indexOf('[') + 1, property.indexOf(']')));
				property = property.substring(0, property.indexOf('['));
			}
			var val = array.length > 0 ? (isArray ? [] : {}) : finalVal;
			if (lastArrayIndex != null) {
				if (isArray) {
					obj[lastArrayIndex][property][indexComp] = val;
				} else {
					obj[lastArrayIndex][property] = val;
				}
			} else {
				if (isArray) {
					obj[property][indexComp] = val;
				} else {
					obj[property] = val;
				}

			}
			lastArrayIndex = indexComp;
			obj = val;
		} else {
			obj = objProperty;
			lastArrayIndex = null;
		}
	}
	var array = xdom.getElementsByAttribute('xvar', v);
	xutil.each(array, function(item) {
		if (item.getAttribute && item.getAttribute("type") == "radio") {
			if (item.value == obj) {
				item.checked = true;
			}
		} else {
			if (item !== document.activeElement) {
				xinputs.setValueOnInput(item, obj);
			}
		}
	});
};
function updateAllObjects() {
	var createdFromInputs = {};
	var existedBefore = {};
	var updated = {};
	xdom.eachInput(function(input) {
		try{
			var v;
			if (input.getVar) {
				v = input.getVar();
			} else {
				v = input.getAttribute('xvar');
			}
			if (v) {
				xlog.debug("updateAllObjects", "Each xvar: " + v + ", id: "
						+ input.id);
				var objName = v.split('.')[0];
				var ctxId;
				if (input.getCtxId) {
					ctxId = input.getCtxId();
				} else {
					ctxId = getElementCtx(input);
				}

				if (!existedBefore[ctxId]) {
					existedBefore[ctxId] = {};
				}
				if (!createdFromInputs[ctxId]) {
					createdFromInputs[ctxId] = {};
				}
				xlog.debug("updateAllObjects", "Each before check exists: "
						+ input.value);
				if (existedBefore[ctxId][objName] == null
						&& createdFromInputs[ctxId][objName] == null) {
					if (existsObject(objName, ctxId)) {
						existedBefore[ctxId][objName] = true;
					} else {
						createdFromInputs[ctxId][objName] = true;
					}
				}
				xlog.debug("updateAllObjects", "Each after check exists: "
						+ input.value);
				if (existedBefore[ctxId][objName]) {
					xlog.debug("updateAllObjects",
							"Each before verify objOrInputProperty: "
									+ input.value);
					grabValueFromPropertyOrInput(v, ctxId);
					xlog.debug("updateAllObjects",
							"Each after verify objOrInputProperty: "
									+ input.value);
				} else if (!updated[objName]) {
					xlog.debug("updateAllObjects", "Each before update: "
							+ input.value);
					updated[objName] = true;
					window['_x_temp_var_'] = buildObj(objName);
					evalOnContext(ctxId, objName + ' = _x_temp_var_');
					window['_x_temp_var_'] = null;
					xlog.debug("updateAllObjects", "Each after update: "
							+ input.value);
				}
				xlog.debug("updateAllObjects", "Each END");
			}
		}catch(e){
			xlog.error("ERROR UPDATING OBJECTS.", e);
		}
	});
	xlog.debug("updateAllObjects", "end update objects");
};
function updateInputs() {
	if (!X._loaded) {
		return;
	}
	var updated = {};
	var list = xvisual.getUpdatables();
	xutil.each(list, function(item){
		item.update();
	});
	var inputList = xdom.getInputs();
	for(var i = 0; i < inputList.length; i++){
		var input = inputList[i];
		var v = input.getVar ? input.getVar() : input.getAttribute('xvar');
		if (v) {
			xlog.debug("updateInputs", "Each xvar: " + v + ", value: "
					+ input.value + ", id: " + input.id);
			var objName = v.split('.')[0];
			if (!updated[objName]) {
				updated[objName] = true;
				loadObj(objName);
			}
			xlog.debug("updateInputs", "Each END xvar: " + v + ", value: "
					+ input.value);
		}
	}
	updateXObjects();
	xlog.debug("updateInputs", "end update inputs");
}

function updateXObjects() {
	var elements = document.getElementsByTagName("xobject");
	xutil.each(elements, function(el) {
		var v = el.getAttribute("xvar");
		try {
			var res = evalOnContext(xobj.getElementCtx(el), v);
			el.innerHTML = res;
		} catch (e) {
		}
	});
}

function getElementCtx(el){
	if(el){
		var ctxId = el.getAttribute("_x_ctx");
		if(!ctxId){
			ctxId = xdom.findAttributeInParent(el, "_x_ctx");
		}
		if(ctxId){
			return ctxId;
		}
	}
	return _mainController;
}

function evalOnContext(ctx, fn) {
	try{
		if (!ctx) {
			return eval(fn);
		} else {
			return (typeof ctx == "string" ? controllers[ctx] : ctx)._x_eval(fn);
		}		
	}catch(e){
		throw e;
	}
}

var _mainController;
function setMainController(c) {
	X.debug("xstartup", "XObj update all objects");
	_mainController = c;
	updateAllObjects();
	updateXObjects();
	X.debug("xstartup", "XObj showing screen");
	try {
		X.debug("xstartup", "XObj calling before show page");
		evalOnContext(c, 'if(X.beforeShowPage){X.beforeShowPage("' + window.location.pathname.substring("%ctx%".length) + '");}');
	} catch (e) {
		xlog.error("xstartup", "XObj error calling init");
		throw e;
	}
	_("_xbodydiv_").style.display = "block";
	_("_xpreloader_").remove();
	try {
		X.debug("xstartup", "XObj calling init");
		evalOnContext(c, 'var __temp_onInit_fn__ = null;' + 
				'try{' + 
				'	var fn = onInit;' + 
				'	__temp_onInit_fn__ = function(){' + 
				'		fn(window["_x_parameters"]);' + 
				'	}' + 
				'}catch(e){' + 
				'	__temp_onInit_fn__ = X.$(function(){' +
				'	}, _x_meta);' + 
				'};' + 
				'if(__xvars__){' + 
				'	var __chord = X.createChord(__xvars__.length, __temp_onInit_fn__);' + 
				'	__xvars__.__exec(__chord);' + 
				'}else{' + 
				'	__temp_onInit_fn__()' + 
				'}');
	} catch (e) {
		xlog.error("xstartup", "XObj error calling init");
		throw e;
	}
}

function evalOnMainController(fn){
	return evalOnContext(_mainController, fn);
}

function bindTo(id, xvar){
	_(id).setAttribute("xvar", xvar);
}

_expose(onController);

_expose(updateObject);
_expose(updateAllObjects);
_expose(updateXObjects);
_expose(updateInputs);
_expose(setMainController);
_external(evalOnContext, "_evalOnContext");
_expose(evalOnContext);
_expose(evalOnMainController);
_expose(init);
_expose(getElementCtx);
_external(bindTo);