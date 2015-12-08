var comps;
var components;
function setComponents(c){
	components = c;
}

function init(){
	try{
		X.debug("xstartup", "XComponents INIT");
		%xcomponents%
		comps = _comps
		X.debug("xstartup", "XComponents setComponents");
		setComponents(components);
		X.debug("xstartup", "XComponents initComponent");
		initComponents();
		X.debug("xstartup", "XComponents buildComponent");
		buildComponents();
	}catch(e){
		var msg = "Error loading custom components: " + e.message;
		xlog.error(msg, e);
		throw new Error(msg);
	}
	X.debug("xstartup", "XComponents INIT done");
}

var registeredObjects = {};
function eachRegisteredObjects(each){
	var list = [];
	for(var k in registeredObjects){
		list = list.concat(registeredObjects[k]);
	}
	xutil.each(list, each);
}

function unregisterObjectContext(ctx){
	delete registeredObjects[ctx];
}

function buildCreateFunction(compName){
	return function(comp, xmeta){
		var result = {
			_x_comp: X.comp[compName]
		};
		for(var k in comp){
			result[k] = comp[k];
		}
		result._x_ctx = xmeta.id;
		return result;
	}
}

function startComp(_html, comp, fnInsert){
	var _div = document.createElement('div');
	_div.innerHTML = _html;
	var xid = comp.xid;
	var len = _div.childNodes.length;
	xutil.range(0, len, function(j){
		if(xid){
			if(j == 0){
				_div.childNodes[0].setAttribute( "_s_xid_", xid);
			}
			if(j == len -1){
				_div.childNodes[0].setAttribute("_e_xid_", xid);
			}					
		}
		fnInsert(_div.childNodes[0]);	
	});
}

function insertComp(handle, xid, beforeInsideAfter){
	var _html = handle._x_comp.getHtml(handle);
	if(handle.innerHTML){
		_html = _html.replace('{xbody}', handle.innerHTML);
	}
	startComp(_html, handle, function(node){
		if(beforeInsideAfter == -1){
			var el = xdom.getElementsByAttribute("_s_xid_", xid)[0] || document.getElementById(xid);
			el.parentNode.insertBefore(node, el);
		}else if(beforeInsideAfter == 0){
			var el = document.getElementById(xid);
			el.appendChild(node);
		}else{
			var el = xdom.getElementsByAttribute("_e_xid_", xid)[0] || document.getElementById(xid);
			el.parentNode.insertBefore(node, el.nextSibling);
		}
		postCreateComp(handle, xid);
		configComps(handle._x_ctx);
	});
}

function postCreateComp(handle, xid){
	if(handle._x_comp.onReady){
		var ctx = {
				ctxId: handle._x_ctx,
				eval: function(s){
					return xobj.evalOnContext(this.ctxId, s);
				}
		}
		handle._x_comp.onReady(handle, ctx);
	};
}

function configComps(ctxId){
	xinputs.setContextOnInputs(ctxId);
	xinputs.configEvents();
}

function initComponents(){
	X.comp = components;
	X.comp.insertBefore = function(handle, xid){
		insertComp(handle, xid, -1);
	};
	X.comp.insertAfter = function(handle, xid){
		insertComp(handle, xid, 1);
	};
	X.comp.append = function(handle, xid){
		insertComp(handle, xid, 0);
	};
	X.comp.registerObject = function(ctx, obj){
		var ctxId = ctx.ctxId || ctx;
		if(!registeredObjects[ctxId]){
			registeredObjects[ctxId] = [];
		}
		registeredObjects[ctxId].push(obj);
	};
	X.comp.updateValue = function(comp){
		xobj.updateObject(comp);
	}
};

function buildComponents(){
	xutil.each(comps, function(comp){
		var compName = comp[0];
		components['new' + compName[0].toUpperCase() + compName.substring(1)] = buildCreateFunction(compName);
	});
}

var _postCreate = [];
function _registerPostCreateComponent(compName, xid, param){
	_postCreate.push([compName, xid, param]);
}

function afterLoadController(ctx){
	var s = _('xpostscript');
	var fn = s.innerText;
	s.parentNode.removeChild(s);
	s = document.createElement('script');
	s.innerText = fn;
	document.body.appendChild(s);
	var fn = __post_xscript__.toString() + ";__post_xscript__();";
	
	xobj.evalOnContext(ctx, fn);
	
	var mainCtxId = ctx || document.body.getAttribute('_x_ctx');
	xutil.each(_postCreate, function(item){
		var comp = item[2];
		comp._x_comp = X.comp[item[0]];
		var xid = item[1] || X.generateId();
		comp._x_ctx = mainCtxId;
		postCreateComp(comp, xid);
	});
	_postCreate = [];
	configComps(mainCtxId);
}

function disableExcept(){
	var xmeta = arguments[arguments.length-1];
	var elements = xdom.getElementsByAttribute('_x_ctx', xmeta.id);
	xutil.each(elements, function(item){
		var id = item.getAttribute("id");
		var found = false;
		for(var i = 0; i < arguments.length - 1; i++){
			if(id == arguments[i]){
				found = true;
				break;
			}
		}
		if(!found){
			item.setAttribute("disabled", true);
		}
	});
}

function disable(varName){
	var elements = xdom.getElementsByAttribute('xvar', varName, true);
	xutil.each(elements, function(item){
		item.setAttribute("disabled", true);
	});
}

_expose(eachRegisteredObjects);
_expose(unregisterObjectContext);
_expose(initComponents);
_expose(buildComponents);
_expose(init);
_expose(afterLoadController);
_external(disableExcept);
_external(disable);
_external(_registerPostCreateComponent);
