function setValueOnInput(input, value){
	xlog.debug("_x_input", "xinputs.setValueOnInput " + (input.getAttribute ? "xvar: " + input.getAttribute("xvar") + ", type: " + input.getAttribute("type") : " no getAttribute ") + ", name: " + input.name + ", valueToSet: " + value + ", id: " + input.id);
	var isJson = input.getAttribute("xisjson") == "true";
	if(input.getAttribute("type") == "checkbox"){
		input.checked = value;
	}else if(input.getAttribute("type") == "radio"){
		var elements = document.getElementsByName(input.getAttribute("name"));
		xlog.debug("_x_input", "setValueOnRadio radio name: " + input.getAttribute("name") + ", len: " + elements.length);
		xutil.each(elements, function(item){
			xlog.debug("_x_input", "radio name: " + input.getAttribute("name") + ", index: " + i + ", value: " + item.value);
			item.checked = (item.value == (value + ""));
			xlog.debug("_x_input", "radio name: " + input.getAttribute("name") + ", aftersSet: " + item.checked);
		});
		xlog.debug("_x_input", "setValueOnRadio radio name: " + input.getAttribute("name") + ", END");
	}else if(isJson){
		if(input.nodeName.toUpperCase() == "SELECT"){
			var options = X.getChildNodesByTagName(input, "option", true);
			for(var i = 0; i < options.length; i++){
				var __xtempvar__;
				var tempval = decodeURIComponent(options[i].getAttribute("value"));
				eval('__xtempvar__=' + tempval);
				if((!__xtempvar__ && !value) || (value && __xtempvar__ && value.id == __xtempvar__.id)){
					options[i].setAttribute("selected", "true");
					break;
				}
			}
		}
	}else if(input.getAttribute("xtype") == 'autocomplete'){
		var ctxId = xobj.getElementCtx(input);
		X.getAutocomplete(input).setInputValue(value);
	}else if(input.value != value){
		if(input.getAttribute('xtype') == 'date'){
			var isDate = value instanceof Date;
			if(isDate || input.getAttribute("xdatetype") == 'true'){
				var dtFormat = input.getAttribute("xdateformat") || '%defaultdateformat%';
				value = xmask.formatDate(value, dtFormat);
				input.setAttribute("xdatetype", 'true');				
			}else{
				input.setAttribute("xdatetype", 'false');
			}
		}else if(input.getAttribute('xtype') == 'ifloat'){
			value = (value + "").replace('.', ',');
		}
		input.value = value;
	}
	xlog.debug("_x_input", "xinputs.setValueOnInput xvar: " + input.getAttribute("xvar") + ", END");
}

function getValueFromInput(input){
	var val;
	var type = input.getAttribute ? input.getAttribute('xtype') : null;
	var checkbox = input.getAttribute ? input.getAttribute('type') == "checkbox" : null;
	var isJson = input.getAttribute("xisjson") == "true";
	if(isJson){
		if(input.nodeName.toUpperCase() == "SELECT"){
			var options = X.getChildNodesByTagName(input, "option", true);
			for(var i = 0; i < options.length; i++){
				if(options[i].getAttribute("selected") == "true"){
					eval('val=' + decodeURIComponent(options[i].getAttribute("value")));
					break;
				}
			}
		}
	}
	if(checkbox){
		val = input.checked;
	}else if (type) {
		xlog.debug("_x_input", "xinputs.getValueFromInput xvar: " + input.getAttribute("xvar") + ", xtype: " + type + ", isjson: " + isJson);
		if (type == 'ifloat'){
			if(input.value.indexOf(',') < 0 && input.value.indexOf('.') < 0){
				type = 'int';
			}else{
				type = 'float';
				input.value = input.value.replace('.', ',');
				var split = input.value.split(',');
				val = split[0] + '.' + xmask.padR(split[1], '0', 2);
				xlog.debug("_x_input", "xinputs.getValueFromInput float val: " + val);
			}
		}
		xlog.debug("_x_input", "xinputs.getValueFromInput new type: " + type + ", value: " + (val || input.value));
		if (type == 'int' || type == 'percent') {
			val = parseInt(xmask.getNumber(val || input.value));
			if(isNaN(val)){
				val = 0;
			}
			xlog.debug("_x_input", "xinputs.getValueFromInput int: " + val);
		} else if (type == 'float') {
			val = parseInt(xmask.getNumber(val || input.value)) / 100;
			if(isNaN(val)){
				val = 0;
			}
			xlog.debug("_x_input", "xinputs.getValueFromInput float: " + val);
		} else if (type == 'boolean') {
			val = input.value.toUpperCase() == 'TRUE';
		} else if (type == 'autocomplete') {
			val = X.getAutocomplete(input).getValue();
			if (!val) {
				val = null;
			}
		} else {
			var inputVal = input.value;
			if(input.getAttribute && type == 'date' && input.getAttribute('xdatetype') == 'true'){
				var dtFormat = input.getAttribute("xdateformat") || '%defaultdateformat%';
				inputVal = xmask.parseDate(inputVal, dtFormat);
			}
			if(input.getAttribute && input.getAttribute("xisjson")){
				if(inputVal){
					eval("val = " + decodeURIComponent(inputVal));
				}else{
					val = null;
				}
			}else{
				val = inputVal;				
			}
		}
	}else if(input.getValue){
		val = input.getValue();
	} else {
		if(input.getAttribute && input.getAttribute("xisjson")){
			if(input.value){
				eval("val = " + decodeURIComponent(input.value));
			}else{
				val = null;
			}
		}else{
			val = input.value;				
		}
	}
	xlog.debug("_x_input", "xinputs.getValueFromInput return: " + val);
	return val;
}

function setContextOnInputs(cid){
	function getCidFrom(obj){
		if(!cid){
			var parent = obj;
			while((parent = parent.parentNode)){
				parentCid = parent.getAttribute("_x_ctx");
				if(parentCid){
					return parentCid;
				}
			}
		}
		return cid;
	}
	xdom.eachInput(function(input) {
		if(!input.getAttribute('_x_ctx')){
			input.setAttribute('_x_ctx', getCidFrom(input));
		}
	}, true);
	xcomponents.eachRegisteredObjects(function(obj){
		obj.setCtx(cid);
	});
	var elements = document.getElementsByTagName("xobject");
	xutil.each(elements, function(el){
		if(!el.getAttribute("_x_ctx")){
			el.setAttribute('_x_ctx', getCidFrom(el));				
		}
	});
}


function createFnEvent(input, eventName, updateInputs) {
	var fn = input.getAttribute("on" + eventName);
	if(!input._x_events){
		input._x_events = {};		
	}
	if(!input._x_events[eventName]){
		input._x_events = [];		
	}
	input.setAttribute("on" + eventName, "");
	input.setAttribute("xon" + eventName, fn || "(function(){})");
	return function(e) {
		return _fireEventAUX(eventName, input, e, updateInputs);
	};
}

function _fireEvent(eventName, idSelOrInput){
	var input = typeof(idSelOrInput) == 'string' ? _(idSelOrInput) : idSelOrInput;
	if(!input){
		return;
	}
	var e = {
		type: eventName,
		target: input
	}
	_fireEventAUX(eventName, input, e, false);
}

function _fireEventAUX(eventName, input, e, updateInputs){
	xlog.debug("_x_event", "Fired Event " + eventName + " of input of xvar " + input.getAttribute("xvar") + ", id: " + input.id);
	for(var index in input._x_events[eventName]){
		input._x_events[eventName][index](input, [e]);
	}
	xobj.updateObject(input);
	xlog.debug("_x_event", "After updateObject ev: " + eventName + " xvar: " + input.getAttribute("xvar") + " val: " + input.value);
	var fn = input.getAttribute("xon" + eventName);
	if(fn){
		xlog.debug("_x_event", "Fireing ev: " + eventName);
		xobj.evalOnContext(xobj.getElementCtx(input), fn);	
		xlog.debug("_x_event", "After fired ev: " + eventName + " xvar: " + input.getAttribute("xvar") + " val: " + input.value);
	}
	if(updateInputs){
		xobj.updateInputs();
		xlog.debug("_x_event", "After updateInputs ev: " + eventName + " xvar: " + input.getAttribute("xvar") + " val: " + input.value);
	}
	xobj.updateXObjects();
	xdom.updateElementsAttributeValue(null, eventName == 'blur' ? null : input);
	xlog.debug("_x_event", "After updateObjectects ev: " + eventName + " xvar: " + input.getAttribute("xvar") + " val: " + input.value);
	var result = xmask.mask(e);
	if(result != undefined && result == false || result == true){
		return result;
	}
}

function configEvent(eventName, updateInputs) {
	xdom.eachInput(function(input){
		if(!input.getAttribute('_x_event_' + eventName)){
			input.setAttribute('_x_event_' + eventName, "true");
			input["on" + eventName] = createFnEvent(input, eventName, updateInputs);								
		}
	}, true);
}

function configEvents(){
	configEvent("keyup", false);
	configEvent("keydown", false);
	configEvent("change", true);
	configEvent("blur", true);
	configEvent("focus", true);
	configEvent("click", true);
	var elArray = xdom.getElementsByAttribute("xtype", 'autocomplete');
	xutil.each(elArray, function(item){
		if(!item._xautocomplete){
			var ac = X.getAutocomplete(item);
			var ctx = xobj.getElementCtx(item);
			if(item.autocompletefunction){
				var fn = evalOnContext(ctx, item.autocompletefunction);
				ac.setSourceFunction(fn);
			}
			if(item.descriptionfunction){
				var fn = evalOnContext(ctx, item.descriptionfunction);
				ac.setDescriptionFunction(fn);
			}
			if(item.finaldescriptionfunction){
				var fn = evalOnContext(ctx, item.finaldescriptionfunction);
				ac.setFinalDescriptionFunction(fn);
			}
		}
	});
}

function validateFields() {
	var result = true;
	var array = xdom.getElementsByAttribute('xmandatory', 'true');
	for(var i in array){
		var item = array[i];
		if (item.value == '') {
			xvisual.highlight(item);
			result = false;
		} else {
			xvisual.removeHighlight(item);
		}
	};
	return result;
}

_external(validateFields);

_expose(setValueOnInput);
_expose(getValueFromInput);
_expose(setContextOnInputs);
_expose(configEvents);
_external(_fireEvent)