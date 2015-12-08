var metaObj = %meta%;

var controllerCallbacks = {}
function importScript(jsName, callback){
	xlog.debug("ximport", "Importing script " + jsName);
	var scr = document.createElement("script");
	var path = "%ctx%" + jsName + '?_xcontroller=true';
	xlog.debug("ximport", "Path: " + path); 
	scr.src = path;
	var _firstScript = document.getElementsByTagName('script')[0];
	_firstScript.parentNode.insertBefore(scr, _firstScript);
	if(!controllerCallbacks[jsName]){
		controllerCallbacks[jsName] = [];
	}
	controllerCallbacks[jsName].push(callback);
}

function importS(jsName, callback){
	importScript(jsName, function(_c){
		var fnName = '__xtemp__fn__' + xutil.generateId();
		X[fnName] = function(){
			callback(_c);
		}
		xobj.evalOnContext(_c, 'var fnName = "' + fnName + 
				'";if(__xvars__){var __chord = X.createChord(__xvars__.length, function(){try{onInit()}catch(e){};X[fnName]()});__xvars__.__exec(__chord);}else{try{onInit()}catch(e){};try{X[fnName]();}catch(e){}}');
	});	
}

function _addController(c){
	xlog.debug("ximport", "_addController cid: " + c._jsName);
	var callback = controllerCallbacks[c._jsName].shift();
	xlog.debug("ximport", "_addController callback: " + callback);
	xobj.onController(c, callback);
	xlog.debug("ximport", "_addController done");
}

function go(path, param) {
	xvisual.showLoading();
	setTimeout(function(){
		if(param){
			path += '?_xjsonxparam=' + encodeURI(xutil.stringify(param));
		}
		var fullPath = '%ctx%' + path;
		window.location.href = fullPath;
	},10);
}

function prepareUrl(url) {
	if (url[0] == '/') {
		url = url.substring(1);
	}
	return url;
}

function getArgs(args){
	var p = Array.prototype.slice.call(args);
	var callback = p.pop();
	var exceptionHandler = null;
	if(typeof(p[p.length -1]) == 'function'){
		
		exceptionHandler = callback;
		callback = p.pop();
	}
	return [p, callback, exceptionHandler];
}

function createRemoteFunction(alias, method){
	var methodName = method.name;
	var type = method.type;
	var nocache = method.nocache;
	function _fillParameters(args){
		var params = [];
		for(var i in args[0]){
			var json = xutil.stringify(args[0][i]);
			params.push("_param" + i + "=" + encodeURIComponent(json));
		}
		if(nocache == "true"){
			params.push("&_xnocache=" + xutil.generateId());
		}
		return params.join("&");
	}
	if(type == 'GET'){
		return function(){
			var args = getArgs(arguments);
			var callback = args[1];
			var exceptionCallback = args[2];
			var paramArray = [alias + '/' + methodName + '?' + _fillParameters(args), callback];
			if(exceptionCallback){
				paramArray.push(exceptionCallback);
			}
			callGET.apply(X, paramArray);
		}
	}else{
		return function(){
			var args = getArgs(arguments);
			var callback = args[1];
			var exceptionCallback = args[2];
			if(_isUploading){
				_isUploading = false;
				if(exceptionCallback){
					paramArray.push(exceptionCallback);
				}
				_upload(alias + '/' + methodName, _fillParameters(args), callback, exceptionCallback);
			}else{
				var paramArray = [alias + '/' + methodName, _fillParameters(args), callback];
				if(exceptionCallback){
					paramArray.push(exceptionCallback);
				}
			
				callPOST.apply(X, paramArray);
			}
		}
	}
}

function ping(onSuccess, onError){
	ajax({
		type : "get",
		url : "%ctx%/x/_x_ping?_x_nocache=" + xutil.generateId(),
		async : true,
		success : onSuccess || function(){xlog.debug("_x_internal", "ping")},
		error : onError || function(e){xlog.error("ping error: " + e.message, e)}
	});
}

function bindService(alias){
	var result = {};
	if(!metaObj[alias]){
		var msg = "Service '" + alias + "' not found!";
		xlog.error(msg);
		throw new Error(msg);
	}
	var meta = metaObj[alias];
	for(var i in meta.methods){
		var method = meta.methods[i].name;
		var type = meta.methods[i].type;
		result[method] = createRemoteFunction(alias, meta.methods[i]);
	}
	return result;
}

function callGET() {
	callAjaxGET(prepareUrl(arguments[0]), arguments[1], arguments[2]);
}

function callPOST() {
	callAjaxPOST(prepareUrl(arguments[0]), arguments[1],
			arguments[2], arguments[3]);
}

function callSync() {
	if (arguments.length == 3) {
		callSyncAjaxGET(prepareUrl(arguments[0]), arguments[1]);
	} else {
		callSyncAjaxPOST(prepareUrl(arguments[0]), arguments[1],
				arguments[2]);
	}
}
function onCallback(c, e, url){
	return function(){
		var result = eval("__x = " + arguments[0]);
		if(result.__response){
			if(c){
				c.apply(null, [result.result]);				
			}
		}else{
			if(result.__not_authenticated){
				X.go('/x/no_authentication');
			}
			if(e){
				e.apply(null, [{
					name: result.exceptionName,
					message: result.message
				}]);				
			}else{
				var msg = "Error calling url: " + url + ".\n Exception: " + result.exceptionName + "\nMessage: " + result.message;
				xlog.error(msg);
				throw msg;
			}
		}
	}
}

function callAjaxGET(purl, success, exception) {
	var url = "%ctx%/x/" + purl;
	ajax({
		type : "get",
		url : url,
		async : true,
		success : onCallback(success, exception, purl),
		error : function(jqXHR, textStatus, errorThrown) {
			// Ignore
		}
	});
}
function callSyncAjaxGET(purl, success, exception) {
	var url = "%ctx%/x/" + purl;
	ajax({
		type : "get",
		url : url,
		async : false,
		success : onCallback(success, exception, purl)
	});
}
function callAjaxPOST(url, param, success, exception) {
	ajax({
		type : "POST",
		url : "%ctx%/x/" + url,
		data : param,
		async : true,
		dataType : "html",
		success : onCallback(success, exception, url)
	});
}

function callSyncAjaxPOST(url, param, success, exception) {
	ajax({
		type : "POST",
		url : "%ctx%/x/" + url,
		data : param,
		async : false,
		dataType : "html",
		success : onCallback(success, exception, url)
	});
}
function ajax(param){
	var type = param.type;
	var url = '%sitedomain%' + param.url;
	var async = param.async || true;
	var data = param.data;
	var success = param.success;

	var xmlhttp;
	if (window.XMLHttpRequest){
		xmlhttp = new XMLHttpRequest();
	}else{
		xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
	}
	xmlhttp.onreadystatechange = function(){
		if (xmlhttp.readyState == 4){
			if(xmlhttp.status == 200){
				success(xmlhttp.responseText);
			}else {
				if(param.error){
					param.error(xmlhttp.status);
				}else{
					if (xmlhttp.status == 0) {
						alert('Sem conexao. Verifique sua rede');
					} else if (xmlhttp.status == 404) {
						alert('Ocorreu um erro (404). Por favor contate o administrador.');
					} else if (xmlhttp.status == 500) {
						alert('Ocorreu um erro (500). Por favor contate o administrador.');
					}
				}
			}
		}
	}
	xmlhttp.open(type, url, async);
	xmlhttp.setRequestHeader("Content-type","application/x-www-form-urlencoded");  
	xmlhttp.send(data);
}

function getHtmlPage(url, callback){
	var _url = "%ctx%" + url + "?_xpopup=true";
	ajax({
		type : "get",
		url : _url,
		async : true,
		success : callback,
		error : function(status) {
			if(403){
				alert('Resource ' + url + ' not found');
			}else{
				alert('Error ' + status + ' on resource ' + url);
			}
		}
	});
}

var _file;
var _formUpload;
var _isUploading;
function prepareForUpload(id){
	var file = document.getElementById(id);
	if(!file.getAttribute("name") || file.getAttribute("name") == ""){
		file.setAttribute("name", "_file_upload_" + id);
	}
	var ifr = document.createElement("iframe");
	ifr.setAttribute("src", "");
	ifr.setAttribute("style", "display: none;");
	ifr.setAttribute("name", "upload_iframe");
	file.parentNode.insertBefore(ifr, file);
	file.parentNode.removeChild(file);
	var frm = document.createElement("form");
	frm.setAttribute("enctype", "multipart/form-data");
	frm.setAttribute("target", "upload_iframe");
	frm.setAttribute("method", "POST");
	frm.appendChild(file);
	ifr.parentNode.insertBefore(frm, ifr.nextSibling);
	
	_file = file;
	_formUpload = frm;
	_isUploading = true;
}

var _callbackUpload = null;
function _upload(url, parameters, callback, onException) {
	_formUpload.setAttribute("action", "%ctx%/x/" + url + "?" + parameters);
	_callbackUpload = onCallback(callback, onException, url);
	_formUpload.submit();
	_formUpload = null;
}

function _uploadResponse(response) {
	_file.value = null;
	_file = null;
	_callbackUpload(response);
	xobj.updateInputs();
}


_expose(importScript);
_external(importS, 'import');
_external(ping);
_external(ajax);
_external(bindService);
_external(prepareForUpload);
_external(getHtmlPage);
_external(go);
_external(_addController);
_external(_uploadResponse);

