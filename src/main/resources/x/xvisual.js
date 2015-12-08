function createEventHandler(idModal, fn) {
	return function() {
		if (fn) {
			fn(idModal);
		}
		closeMsg(idModal);
	};
}

var popups = {};

function popup(obj){
	showLoading();
	obj.actions = null;
	xremote.getHtmlPage(obj.url, function(html){
		obj.msg = html;
		var _tempDiv = document.createElement("div");
		_tempDiv.innerHTML = html;
		var modalInfo = xdom.getChildComponentsByName(_tempDiv, 'modal-info', true)[0];
		obj.size = {
			width : modalInfo.width,
			height : modalInfo.height
		};
		obj.title = modalInfo.title;
		obj._popup = true;
		var idModal = msg(obj);
		var elModal = _(idModal);
		modalInfo._gen_id = idModal;
		popups[modalInfo.id] = modalInfo;
		xcomponents.buildComponents();
		xremote.importScript(obj.url + ".js", function(_c){
			popups[modalInfo.id]._ctx = _c._x_getControllerId();
			_tempDiv.setAttribute("_x_ctx", _c._x_getControllerId());
			xobj.updateAllObjects();
			xobj.updateXObjects();
			if(obj.showLoading){
				X.showLoading();
			}
			var fnName = '__xtemp__fn__' + xutil.generateId();
			var beforeShowModal = X.beforeShowModal;
			try {
				X.debug("xstartup", "XObj calling before show modal");
				xobj.evalOnContext(_c, 'if(X.beforeShowModal){X.beforeShowModal("' + obj.url.substring("%ctx%".length) + '");}');
			} catch (e) {
				xlog.error("xstartup", "XObj error calling init");
				throw e;
			}
			_("_xbodydiv_" + obj.url).style.display = "block";
			_("_xpreloader_").remove();
			obj._element.style.display = 'block';
			if(_c.onInit){
				X[fnName] = function(){
					_c.onInit(function(){
						xobj.updateInputs();
						if(obj.callback){
							obj.callback.apply(null, arguments);	
						}
					}, obj.parameter);
				}
			}else{
				X[fnName] = function(){}
			}
			xcomponents.afterLoadController(_c._x_getControllerId());
			xobj.evalOnContext(_c, 'var fnName = "' + fnName + '";' + 
					'if(__xvars__){' + 
					'	var __chord = X.createChord(__xvars__.length, function(){' + 
					'		X[fnName]()' + 
					'	});' + 
					'	__xvars__.__exec(__chord);' + 
					'}else{' + 
					'	try{' + 
					'		X[fnName]();' + 
					'	}catch(e){}' + 
					'}');
		});
		setTimeout(function(){closeLoading(true, function(){setBlurryBackground(true);});}, 100);
	});
}

function closePopup(id){
	var ctxId = popups[id]._ctx;
	delete _registeredUpdatables[ctxId];
	xcomponents.unregisterObjectContext(ctxId);
	closeMsg(popups[id]._gen_id);
}

function msg(obj) {
	if (!obj.size) {
		obj.size = {
				width : 400,
				height : 200
		};
	}
	var idModal = "modal_" + xutil.generateId();
	var left = parseInt((window.innerWidth - obj.size.width) / 2);
	var top = parseInt((window.innerHeight - obj.size.height) / 2);
	var html = '%modaltemplate%'.replace('{obj.title}', obj.title)
		.replace('{obj.size.width}', obj.size.width)
		.replace('{obj.size.height}', obj.size.height)
		.replace('{obj.msg}', obj.msg)
		.replace('{obj.left}', left)
		.replace('{obj.top}', top)
		.replace('{idModal}', idModal);
	var elmod = buildHtmlNodeFromString(html);	
	elmod.setAttribute("id", idModal);
	elmod.innerHTML = html;
	setBlurryBackground(true);
	if(obj._popup){
		elmod.style.display = 'none';
		obj._element = elmod;
	}
	document.body.appendChild(elmod);
	var b_ids = {};
	for( var i in obj.actions) {
		var action = obj.actions[i];
		var id = '_a_btn_' + xutil.generateId();
		b_ids[id] = action.fn;
		var btn = document.createElement("button");
		btn.id = id;
		btn.innerHTML = action.label;
		X.onAddModalButton(idModal, btn, action);
	}
	
	for( var k in b_ids) {
		_(k).setAttribute('_x_event_click', "true");
		_(k).onclick = createEventHandler(idModal,
				b_ids[k]);
	}
	return idModal;
}

function buildHtmlNodeFromString(html){
	var dv = document.createElement("div");
	dv.innerHTML = html;
	for(var i = 0; i < dv.childNodes.length; i++){
		if(dv.childNodes[i].nodeType == 1){
			return dv.childNodes[i];
		}
	}
}

var blurryBackground = false;
var blurryBckTimeout;
function setBlurryBackground(on){
	blurryBackground = on;
	if(blurryBckTimeout){
		clearTimeout(blurryBckTimeout);
	}
	blurryBckTimeout = setTimeout(function(){
		_('_xbodydiv_').setAttribute("style", on ? "-webkit-filter: blur(1px); -moz-filter: blur(1px);-o-filter: blur(1px);-ms-filter: blur(1px);filter: blur(1px);" : "");
	}, 100);
}

function closeMsg(idModal) {
	var dv = _(idModal);
	dv.parentNode.removeChild(dv);
	setBlurryBackground(false);
	xobj.updateInputs();
}

var isShowingLoading = false;
function showLoading() {
	if(!isShowingLoading){
		isShowingLoading = true;
		setBlurryBackground(true);
		var dv = document.createElement("div");
		dv.setAttribute("style","background:{{backgroundLoader}};width: 100%;margin: 0;position: fixed;height: 100%;left: 0;top: 0;border: 0;" +
				"-webkit-border-radius: 0;-moz-border-radius: 0;-o-border-radius: 0;border-radius: 0;z-index: 3333;");
		var idModal = "_loading_modal_";
		dv.setAttribute("id", idModal);
		var size = 40;
		var left = parseInt((window.innerWidth - size) / 2);
		var top = parseInt((window.innerHeight - size) / 2);
		var style = "style='position: relative;width: " + size + "px; height: " + size
				+ "px; left: " + left + "px; top: " + top + "px;'";

		var html = '<img ' + style
				+ ' src="%ctx%/x/loader.gif"/>';

		dv.innerHTML = html;
		document.body.appendChild(dv);
	}
}

function closeLoading(noupdate, cb) {
	if(isShowingLoading){
		setTimeout(function(){
			if(!noupdate){
				xobj.updateInputs();
			}
			var dv = _("_loading_modal_");
			dv.parentNode.removeChild(dv);
			setBlurryBackground(false);
			isShowingLoading = false;
			cb();
		}, 200);
	}
}
var selectWithList = {};
var selectWithModel = {}
var tablesWithModel = {};
var tablesWithList = {};
function createUpdatable(id, item, fn, list){
	var result = {
		update: function(){
			if(!_(id)){
				var ind = list.indexOf(result);
				if(ind >= 0){
					list.splice(ind, 1);
				}
			}else{
				fn(id, item);	
			}
		}
	};
	list.push(result);
}
var _registeredUpdatables = {};
function registerUpdatable(ctx, comp){
	var ctxId = ctx.ctxId || ctx;
	if(!_registeredUpdatables[ctxId]){
		_registeredUpdatables[ctxId] = [];
	}
	_registeredUpdatables[ctxId].push(comp);
}
function getUpdatables(){
	var list = [];
	for(var k in selectWithList){
		if(selectWithList[k]){
			createUpdatable(k, selectWithList[k], _fillSelect, list);
		}
	}
	for(var k in selectWithModel){
		if(selectWithModel[k]){
			createUpdatable(k, selectWithModel[k], _fillSelectWithModel, list);			
		}
	}
	for(var k in tablesWithModel){
		if(tablesWithModel[k]){
			createUpdatable(k, tablesWithModel[k], _fillTableWithModel, list);
		}
	}
	for(var k in tablesWithList){
		if(tablesWithList[k]){
			createUpdatable(k, tablesWithList[k], _fillTable, list);			
		}
	}
	for(var ctx in _registeredUpdatables){
		list = list.concat(_registeredUpdatables[ctx]);
	}
	return list;
}
function clearTable(id){
	tablesWithModel[id] = null;
	tablesWithList[id] = null;
	var t = _(id);
	var node = xdom.getChildNodesByTagName(t, 'tbody', true);
	if(node){
		node[0].innerHTML = '';
	}
}

function addToTable(id, obj, clear) {
	if(!(obj instanceof Array)){
		obj = [obj]
	}
	fillTable(id, obj, true);
}

function _fillTableWithModel(id, model) {
	clearTable(id);
	tablesWithModel[id] = model;
	var t = _(id);
	var bd = xdom.getChildNodesByTagName(t, 'tbody', true);
	xutil.range(0, model.getSize(), function(i) {
		var tr = document.createElement("tr");
		tr.setAttribute("class", i % 2 == 0 ? "" : "xodd");
		tr._id_row = i;
		if(model.onRowClick){
			tr.onclick = function(){
				model.onRowClick(this._id_row, this);
			};
		}
		xutil.range(0, model.getColsQuantity(), function(j) {
			var td = document.createElement("td");
			var value;
			try{
				value = model.getHtml(i, j);
			}catch(e){
				xlog.error("Error in table model:" + e.message);
				value = "";
			}
			td.innerHTML = value || '';
			td._id_row = tr._id_row;
			var colStyle = model.getColStyle(i, j);
			if(colStyle){
				td.setAttribute("style", colStyle);
			}
			td._id_col = j;
			if(model.onColClick){
				td.onclick = function(){
					model.onColClick(this._id_row, this._id_col, this);
				};
			}
			tr.appendChild(td);
		});
		bd[0].appendChild(tr);
	});
	updateInputs();
}

function updateInputs(){
	xinputs.setContextOnInputs();
	xinputs.configEvents();	
}

function fillTable(id, modelOrList, keep){
	if(modelOrList instanceof Array){
		_fillTable(id, modelOrList, keep);
	}else{
		_fillTableWithModel(id, modelOrList);
	}
}

function _fillTable(id, lista, keep) {
	if(!keep){
		clearTable(id);
	}
	tablesWithList[id] = lista;
	var t = _(id);
	var th = xdom.getChildNodesByTagName(t, 'thead')
	var trList = xdom.getChildNodesByTagName(th[0], 'tr')
	var cols = [];
	xutil.each(trList[0].childNodes, function(node){
		var nm = node.nodeName;
		if (nm && nm.toUpperCase() == "TH") {
			var v = node.getAttribute("xvar");
			var item = {}
			if(v){
				item.prop = v;	
			}
			var h = node.getAttribute("html");
			if(h){
				item.html = h;
			}
			cols.push(item);
		}
	});
	var bd = xdom.getChildNodesByTagName(t, 'tbody', true);
	xutil.each(lista, function(item, i) {
		var tr = document.createElement("tr");
		tr.setAttribute("class", i % 2 == 0 ? "" : "xodd");
		var _temp_obj_ = item;
		xutil.each(cols, function(col) {
			var td = document.createElement("td");
			var html = '';
			var val = '';
			if(col.prop){
				var prop = "_temp_obj_." + col.prop;
				try{
					val = eval(prop);	
				}catch(e){
					val = '';
				}
			}
			if (col.html) {
				html += col.html + ' ';
			}
			td.innerHTML = html + (val || "");
			tr.appendChild(td);
		});
		bd[0].appendChild(tr);
	});
	updateInputs();
}

function fillSelect(id, listOrModel){
	if(listOrModel instanceof Array){
		_fillSelect(id, listOrModel);
	}else{
		_fillSelectWithModel(id, listOrModel);
	}
}

function _fillSelectWithModel(id, model){
	//TODO se id for null ou comp null gerar erro
	var comp = _(id);
	var selected = comp.selectedIndex;
	if(selected == null || selected < 0){
		for(var i = 0; i < comp.options.length; i++){
			if(comp.options[i].getAttribute("selected")){
				selected = i;
			}
		}
	}
	if(model.isJson()){
		comp.setAttribute("xisjson","true");
	}
	comp.innerHTML = '';
	selectWithModel[id] = model;
	var _s = createNullOption(comp, model.isJson());
	if(selectAllowNull(comp)){
		selected--;
	}
	xutil.range(0, model.getSize(), function(i){
		_s += '<option value="';
		_s += model.getValue(i);
		_s += '"';
		if(selected == i){
			_s += " selected=\"true\" ";
		}
		_s += ">";
		_s += model.getDescription(i);
		_s += '</option>';
	});
	comp.innerHTML = _s;
}

function selectAllowNull(comp){
	return (comp.getAttribute("allownull") || '').toLowerCase() == 'true';
}

function createNullOption(comp, isJs){
	var _s = "";
	if(selectAllowNull(comp)){
		_s += "<option value='";
		if(isJs){
			_s += 'null';
		}
		_s += "'>" + (comp.getAttribute("nulldescription") || "") + "</option>";
	}
	return _s;
}

function _fillSelect(id, list){
	selectWithList[id] = list;
	var comp = _(id);
	var selected = comp.selectedIndex;
	comp.innerHTML = '';
	var _s = "";
	var isJs = false;
	if(!comp.getAttribute("valueproperty") && list && list.length > 0 && typeof(list[0]) == 'object'){
		isJs = true;
		comp.setAttribute("xisjson","true");
	}
	_s += createNullOption(comp, isJs);
	if(selectAllowNull(comp)){
		selected--;
	}
	xutil.each(list, function(item, index){
		_s += "<option value='";
		var equal = false;
		if(comp.getAttribute("valueproperty")){
			_s += item[comp.getAttribute("valueproperty")];
		}else if(isJs){
			_s += encodeURIComponent(X.stringify(item));
		}else{
			_s += item;
		}
		_s += "' ";
		if(selected == index){
			_s += " selected=\"true\" ";
		}
		_s += ">";
		if(comp.getAttribute("descriptionproperty")){
			_s += item[comp.getAttribute("descriptionproperty")];
		}else if(comp.getAttribute("descriptionscript")){
			window['_temp_combo_item_'] = item;
			var varName = comp.getAttribute("xvarname");
			_s += eval('function _temp_combo_fn_(){ var ' + varName + ' = _temp_combo_item_; return ' + comp.getAttribute("descriptionscript") + '};_temp_combo_fn_();');
		}else{
			_s += item;
		}
		_s += '</option>';
	});
	comp.innerHTML = _s;
}

function onAddModalButton(idModal, button, properties) {
	var modal = _(idModal);
	xdom.getElementsByAttribute('button_place', null)[0].appendChild(button);
}

function _iterHighlightFields(fields, each){
	fields = fields instanceof Array ? fields : [fields]
	xutil.each(fields, function(field){
		if(typeof(field) == 'string'){
			xutil.each(xdom.getElementsByAttribute('xvar', field), function(item){
				each.call(null, item);
			});
		}else{
			each.call(null, field);
		}			
	});
}

function highlight(fields) {
	_iterHighlightFields(fields, onHighlight);
}

function onHighlight(obj) {
	obj.style.border = "1px solid red";
}

function removeHighlight(fields) {
	_iterHighlightFields(fields, onRemoveHighlight);
}

function onRemoveHighlight(obj) {
	obj.style.border = "1px solid #e3e3e3";
}

var autocompleteInstances = {};
function getAutocomplete(param){
	if(typeof(param) == 'string'){
		return autocompleteInstances[param];
	}else{
		if(!param._xautocomplete){
			autocomplete(param, param.getAttribute("xautocompleteclass"), param.getAttribute("xautocompleteselecteditemclass"));
		}
		return param._xautocomplete;
	}
}
function autocomplete(param, autocompleteClass, selectedItemClass) {
	return new function(){
		function getI(){
			return typeof(param) == 'string' ? _(param) : param;
		}
		var idInput = getI().getAttribute("id") || X.generateId();
		function getAC(){
			return _('_xautocomplete_' + idInput);
		}
		xlog.debug('autocomplete', 'Started for input ' + idInput);
		var sourceFunction = null;
		var thisObj = this;
		autocompleteInstances[idInput] = thisObj;
		getI()._xautocomplete = thisObj;
		this.setDescriptionFunction = function(fndesc){
			this._fndesc = stripFunction(fndesc);
		};
		this.setFinalDescriptionFunction = function(fn){
			var fnfinaldesc = stripFunction(fn);
			this._fnfinaldesc = fnfinaldesc;
			getI()._xautocompletefnfinaldesc = fnfinaldesc;
		};
		this.setOnSetValueFunction = function(f){
			this.onSetValue = stripFunction(f);
		};
		this.setSourceList = function(list){
			sourceFunction = function(typed){
				if(typed != ''){
					var countFound = 0;
					var selList = [];
					for (var i in list) {
						if (thisObj._fnfinaldesc(list[i]).toUpperCase().indexOf(typed.toUpperCase()) >= 0) {
							countFound ++;
							selList.push(list[i]);
						}
						if(countFound > 2){
							break;
						}
					}
					createDropDown(selList);
				}else{
					getAC().innerHTML = '';
					getAC().setAttribute('xsel', null);
					getAC().style.visibility = 'hidden';
				}
			};
		};
		this.setSourceFunction = function(fn){
			fn = stripFunction(fn);
			sourceFunction = function(typed){
				fn(typed, createDropDown, thisObj);
			};
		};
		var _afterSet = [];
		this.addAfterSetListener = function(fn){
			_afterSet.push(stripFunction(fn));
		};
		this.setInputValue = function(v){
			var obj;
			if(this.onSetValue){
				obj = this.onSetValue(v);
			}else{
				obj = v;
			}
			this.setValue(obj);
		};
		this.setValue = function(obj){
			var finalDesc = '';
			if(obj){
				finalDesc = thisObj._fnfinaldesc(obj);
			}
			getI()._xautocompletevalue = obj;
			getI().value = finalDesc || '';
			for(var i in _afterSet){
				_afterSet[i](obj, thisObj);
			}
		};
		this.getValue = function(){
			return getI()._xautocompletevalue;
		};
		var fixInterval = null;
		var timeout = null;
		var input = getI();
		input._xcurrentList = null;
		
		var selectCurrent = function(){
			var index = parseInt(getAC().getAttribute('xsel'));
			var input = getI();
			var currentList = input._xautocompletecurrentList;
			var finalDesc = input._xautocompletefnfinaldesc(currentList[index]);
			getI().value = finalDesc;
			getAC().innerHTML = '';
			getAC().style.visibility = 'hidden';
		};
		var styleAutocomplete = '';
		var styleAutocompleteUl = '';
		var styleAutocompleteLi = '';
		var styleAutocompleteA = '';
		var styleAutocompleteASel = '';
		if(!autocompleteClass){
			styleAutocomplete = 'position: absolute;' +
				'border-left: 1px solid #a3bac8;' +
				'border-right: 1px solid #a3bac8;' +
				'border-bottom: 1px solid #a3bac8;' +
				'border-top: 1px solid #a3bac8;' +
				'margin-top: -3px;' +
				'padding-top: 3px;' +
				'z-index: 9999;' +
				'text-align: left;' +
				'background-color: #f6fbfd;';
			
			styleAutocompleteUl = 'margin: 5px;';

			styleAutocompleteLi = 'list-style: none;' +
				'display: block;' +
				'position: relative;' +
				'padding: 5px;' +
				'z-index: 1;';

			styleAutocompleteA = 
				'font-family: Lucida Grande, Lucida Sans, Arial, sans-serif;' +
				'display: block;' +
				'text-decoration: none;' +
				'position: relative;' +
				'z-index: 0;' +
				'font-size: 12px;' +
				'cursor: pointer;';

			autocompleteClass = '';
		}
		var selectedItemStyle = '';
		var unSelectedItemStyle = '';
		if(!selectedItemClass){
			selectedItemClass = '';
			selectedItemStyle = 'background-color: #0d93b6;color: #f6fbfd;'
			unSelectedItemStyle = 'color: #869aa5;';
		}else{
			selectedItemStyle = '';
			unSelectedItemStyle = '';
		}
		var createDropDown = function(list){
			input._xautocompletecurrentList = list;
			var html = '';
			for (var i in list) {
				html += '<li style="' + styleAutocompleteLi + '"><a style="' + styleAutocompleteA + '" xindex="' + i + '">'
						+ thisObj._fndesc(list[i]) + '</a></li>';
			}
			getAC().innerHTML = html == '' ? '' : '<ul style="' + styleAutocompleteUl + '">' + html + '</ul>';
			if(html != ''){
				setTimeout(function(){
					//workaround
					var fixPos = function(){
						setTimeout(function(){
							getAC().style.left = (getI().offsetLeft) + 'px';
							getAC().style.top = (getI().offsetTop + getI().offsetHeigth) + 'px';		
							if(getAC().offsetLeft != getI().offsetLeft && getAC().offsetTop != getI().offsetTop + getI().offsetHeight){
								fixPos();
							}
						},1);
					};
					fixPos();
					fixInterval = setInterval(function(){
						try{
							getAC().style.left = (getI().offsetLeft) + 'px';
							getAC().style.top = (getI().offsetTop + getI().offsetHeight) + 'px';		
						}catch(e){
							clearInterval(fixInterval);
						}
					},100);
					setTimeout(function(){
						getAC().style.visibility = 'visible';
					},200);
						
				},10);
				
				_autocomplete_select();
				
				var aList = xdom.getChildNodesByTagName(getAC(), 'a', true);
				
				function mouseover(e){
					if(!e){
						e = window.event;
					}
					var index = parseInt(e.target.getAttribute('xindex')) ;
					_autocomplete_select(index);
				}
				function click(e){
					selectCurrent();
				}
				xutil.each(aList, function(item){
					item.onmouseover = mouseover;
					item.onclick = click;
				});
			}else{
				getAC().setAttribute('xsel', null);
				if(fixInterval){
					clearInterval(fixInterval);
				}
				getAC().style.visibility = 'hidden';
			}
		};
		
		getI().setAttribute('xtype', 'autocomplete');
		var dv = document.createElement('div');
		dv.setAttribute("class", autocompleteClass);
		dv.setAttribute("style", styleAutocomplete);
		dv.setAttribute("id", '_xautocomplete_' + idInput);
		getI().parentNode.insertBefore(dv, getI().nextSibling);
		getAC().style.width = getI().style.width;
		getI().setAttribute("autocomplete", "off");
		getAC().style.visibility = 'hidden'
		
		var _autocomplete_select = function(index){
			var elements = xdom.getChildNodesByTagName(getAC(), 'a', true);
			xutil.each(elements, function(item){
				item.className = "";
				item.parentNode.setAttribute("style", styleAutocompleteA + unSelectedItemStyle);
			});
			if(!index || index < 0)
				index = 0;
			if(index >= elements.length){
				index = elements.length - 1;
			}
			getAC().setAttribute('xsel', index);
			elements[index].className = selectedItemClass;
			elements[index].parentNode.setAttribute("style", styleAutocompleteA + selectedItemStyle);
			
		};
		if(fixInterval){
			clearInterval(fixInterval);
		}
		var _startBox = function(){
			if (timeout) {
				clearTimeout(timeout);
			}
			timeout = setTimeout(function() {
				if(sourceFunction)
					sourceFunction(getI().value);
			}, 200);
		};
		if(!getI()._x_events){
			getI()._x_events = {}
		}
		if(!getI()._x_events['keyup']){
			getI()._x_events['keyup'] = [];
		}
		getI()._x_events['keyup'].push(function(event) {
			var k = null;
			if (window.event) {
				event = window.event;
				k = event.keyCode;
			} else if (event.which) {
				k = event.which;
			}
			var index = parseInt(getAC().getAttribute('xsel'));
			if (k == 13) {
				selectCurrent();
			} else if(k == 38){
				_autocomplete_select(index-1);
			} else if(k == 40){
				_autocomplete_select(index+1);
			} else {
				_startBox();
			}
		});
		if(!getI()._x_events){
			getI()._x_events = {}
		}
		if(!getI()._x_events['focus']){
			getI()._x_events['focus'] = [];
		}
		getI()._x_events['focus'].push(function(event) {
			if (window.event) {
				event = window.event;
			}
			setTimeout(function() {
				_startBox();
			}, 500);
		});
		if(!getI()._x_events){
			getI()._x_events = {}
		}
		if(!getI()._x_events['blur']){
			getI()._x_events['blur'] = [];
		}
		getI()._x_events['blur'].push(function(event) {
			if (window.event) {
				event = window.event;
			}
			var input = getI();
			var currentList = input._xautocompletecurrentList;
			var index = getAC().getAttribute('xsel');
			if(!index){
				for(var i in currentList){
					if(getI().value.toUpperCase() == input._xautocompletefnfinaldesc(currentList[i]).toUpperCase()){
						index = i;
						break;
					}
				}
			}
			
			if(currentList && currentList.length > 0 && index != null){
				thisObj.setValue(currentList[index]);
			}else{
				thisObj.setValue(null);
			}
			setTimeout(function() {
				if(fixInterval){
					clearInterval(fixInterval);
				}
				getAC().style.visibility = 'hidden';
			}, 50);
		});
		var autocompleteFunction = getI().getAttribute("xautocomplete");
		if(autocompleteFunction){
			this.setSourceFunction(createAutoCompleteFunction(getI(), autocompleteFunction));
		}
		var itemDescriptionFn = createDescFunction(input, "xitemdescription");
		if(itemDescriptionFn){
			this.setDescriptionFunction(itemDescriptionFn);
		}
		var descriptionFn = createDescFunction(input, "xdescription");
		if(descriptionFn){
			this.setFinalDescriptionFunction(descriptionFn);
		}else{
			descriptionFn = getI().getAttribute("xdescriptionfunction");
			if(descriptionFn){
				this.setFinalDescriptionFunction(createDescriptionFunction(getI(), descriptionFn));
			}
		}
	}
};

function createAutoCompleteFunction(input, fnName){
	return function(typed, callback, thisObj){
		var fn = xobj.evalOnContext(xobj.getElementCtx(input), fnName);
		if(fn){
			fn(typed, callback, thisObj);
		}
	}
}
function createDescriptionFunction(input, fnName){
	return function(item){
		var fn = xobj.evalOnContext(xobj.getElementCtx(input), fnName);
		if(fn){
			return stripFunction(fn)(item);
		}
		return null;
	}
}
function createDescFunction(input, typeFn){
	if(input.getAttribute(typeFn)){
		var fn = function(item){
			var scriptFn = input.getAttribute(typeFn);
			var ctx = xobj.getElementCtx(input);
			var varName = input.getAttribute('xvarname');
			window['_x_temp_autocomplete_item'] = item;
			return xobj.evalOnContext(ctx, 'function _x_autocomplete_' + typeFn + '(){var ' + varName + ' = window["_x_temp_autocomplete_item"]; return ' + scriptFn + '};_x_autocomplete_' + typeFn + '();');
		}
		return fn;
	}
}

var iterators = {};
function __registerIterator(id, listName, itemVarName, indexVarName, html){
	iterators[id] = {
		html: html,
		listVar: listName,
		itemVar: itemVarName,
		indexVar: indexVarName 
	}
}

var countUpdateIterator = 0;
var currentIteratorClass;
var nextIteratorClass;
function updateIterators(){
	var elIterArray = _c("__xiteratorprint__");
	for(var i = 0; i < elIterArray.length; i++){
		elIterArray[i].remove();
	}
	currentIteratorClass = '__xiterator__' + countUpdateIterator++;
	nextIteratorClass = '__xiterator__' + countUpdateIterator;
	var updated = {}
	while((elIterArray = _c(currentIteratorClass)).length > 0){
		updateIterator(elIterArray[0]);		
	}
}

function updateIterator(el, prev){
	xdom.removeClass(el, currentIteratorClass);
	xdom.addClass(el, nextIteratorClass);
	var id = el.getAttribute("xiteratorid");
	var type = el.getAttribute("xiteratortype");
	var iterator = iterators[id];
	var html = iterator.html.replace(/\{:/g, "<");
	var ctx = xobj.getElementCtx(el);
	var listVar = iterator.listVar;
	var itemVar = iterator.itemVar;
	var indexVar = iterator.indexVar;
	var indParentVar = xdom.findAttributeInParent(el, "xiteratortempvar");
	var list;
	try{
		var _listVarName = listVar;
		if(prev && indParentVar != null){
			_listVarName = prev[indParentVar] + _listVarName;
		}
		list = xobj.evalOnContext(ctx, _listVarName);
	}catch(e){}
	if(list && list.length){
		var itemVarListArray = [];
		function ctype(html){
			var parent;
			var resultHtml = [];
			function fill(j, js){
				var fn = 'function __temp_fn__(){';
				var itemIndexVar = 'var ' + (indexVar && indexVar != 'null' ? indexVar : '__temp_index_var') + ' = ' + j + ';';
				var itemListVar = 'var ' + itemVar + ' = ' + listVar + '[' + j + '];';
				
				var vars = itemIndexVar + itemListVar;
				if(prev && indParentVar != null){
					vars = prev[indParentVar] + vars;
				}
				itemVarListArray[j] = vars;
				fn += vars;
				fn += ';return ' + js.replace(/!#!/g, '"') + ';};__temp_fn__();';
				return fn;
			}
			for(var j = 0; j < list.length; j++){
				var htmlTemp = html;
				try{
					var fnReplace = function(m, js){
						try{
							return xobj.evalOnContext(ctx, fill(j, js));
						}catch(e){
							xlog.error("Error on xIterator, xattrobject replace", e);
							return '';
						};
					};
					htmlTemp = htmlTemp.replace(/<xobject xvar=.(.*?).><\/xobject>/g, fnReplace);
					htmlTemp = htmlTemp.replace(/<xattrobject xvar=.(.*?).><\/xattrobject>/g, fnReplace);
					htmlTemp = htmlTemp.replace(/_outxdynattr_.*?=.##(.*?)##./g, fnReplace);
					fnReplace = function(m, js){
						return "xvar=\"" + listVar + '[' + j + '].';
					};
					htmlTemp = htmlTemp.replace(new RegExp('xvar=\"(' + itemVar + ')\.', 'g'), fnReplace);
					fnReplace = function(m, js){
						return "xvar='" + listVar + '[' + j + '].';
					};
					htmlTemp = htmlTemp.replace(new RegExp('xvar=\'(' + itemVar + ')\.', 'g'), fnReplace);
					
					fnReplace = function(m, nodeName){
						return "<" + nodeName + " xiteratortempvar='" + j + "' ";
					};
					htmlTemp = htmlTemp.replace(/<([^\s>]*)/, fnReplace);
					
					resultHtml.push(htmlTemp);
				}catch(e){
					xlog.error("Error on xIterator", e);
				}
			}
			
			if(type != 'sibling'){
				parent = el;
			}else{
				parent = el.parentNode;
			}
			var html = [];
			if(parent.children.length == 0){
				html = [resultHtml.join(" ")];
			}else{
				for(var i = 0; i < parent.children.length; i++){
					var c = parent.children[i];
					html.push(c.outerHTML);
					if(type == 'sibling' && c.getAttribute("xiteratorid") == id){
						html.push(resultHtml.join(" "));
					}else if(type != 'sibling' && i == parent.children.length - 1){
						html.push(resultHtml.join(" "));
					}
				}
			}
			return [html.join(""), parent];
		}
		var res = ctype(html, el);
		var parent = res[1];
		parent.innerHTML = res[0];
		var childIterators = xdom.getChildNodesByClassName(parent, "__xiterator__", true, true);
		for(var i = 0; i < childIterators.length; i++){
			updateIterator(childIterators[i], itemVarListArray);
		}
	}
}

_external(__registerIterator);
_external(popup);
_external(closePopup);
_external(msg);
_external(closeMsg);
_external(showLoading);
_external(closeLoading);
_external(clearTable);
_external(addToTable);
_external(fillTable);
_external(fillSelect);
_external(onAddModalButton);
_external(highlight);
_external(onHighlight);
_external(removeHighlight);
_external(onRemoveHighlight);
_external(getAutocomplete);
_expose(getUpdatables);
_expose(updateIterators);
_external(registerUpdatable);
_expose(buildHtmlNodeFromString);



