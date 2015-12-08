console.log("INIT X..");

function _(id, _doc){
	return (_doc || document).getElementById(id);
}

function _c(classes, _doc){
	var l = [];
	var cArray = classes.split(' ');
	for(var i = 0; i < cArray.length; i++){
		var ela = (_doc || document).getElementsByClassName(cArray[i]);
		for(var j = 0; j < ela.length; j++){
			l.push(ela[j]);
		}
	}
	return l;
}

var X = new function() {
	var isDevMode = %xdevmode%;
	this.isDevMode = isDevMode;
	this.temp = {};
	
	function _(id, _doc){
		return (_doc || document).getElementById(id);
	}
	var thisX = this;
	function externalExpose(owner, fn){
		return function(){
			return fn.apply(owner, arguments);
		}
	};
	function xexpose(owner, fn, external, name){
		if(!name){
			name = fn.name;
		}
		if(owner[name]){
			throw new Error("Function " + name + " already exposed in module");
		}
		owner[name] = fn;
		if(external){
			if(thisX[name]){
				throw new Error("Function " + name + " already exposed in X");
			}
			thisX[name] = externalExpose(owner, fn);
		}
	}
	function addModule(moduleFunction){
		return new moduleFunction();
	}
	
	%xmodulescripts%
	
	var updateDisabled = false;
	function xtemporaryDisableUpdates(){
		updateDisabled = true;
	}
	function stripFunction(fn){
		return fn._fn || fn;
	}
	this.$ = function(fn, xmeta){
		var _f = function(){
			var disabled = false;
			if(!updateDisabled){
				disabled = true;
				updateDisabled = true;
			}
			xlog.debug("$", "BEFORE: Intercepting " + fn);
			var r;
			try{
				r = fn.apply(this, arguments);	
			}catch(e){
				if(!e._xcatch){
					var msg = "Error calling function " + fn + ". Error: " + e.message;
					xlog.error(msg, e);
					alert(msg);
					e._xcatch = true;					
				}
				throw e;
			}
			xlog.debug("$", "AFTER: Intercepting " + fn);
			if(disabled){
				xvisual.updateIterators();
				xdom.updateElementsAttributeValue();
				xinputs.configEvents();
				xobj.updateInputs();
				xobj.updateAllObjects();
				xobj.updateXObjects();
				updateDisabled = false;
			}
			return r;
		};
		_f._fn = fn;
		return _f;
	};
};
console.log("STARTING X..");
X.onStart();
console.log("STARTED X");