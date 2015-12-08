var _hasStorage = typeof (Storage) !== "undefined" && window.xuser;

if(window['_x_page_timestamp_'] != window['_x_application_timestamp_'] && !isDevMode){
	location.reload(true);
}

function putValue(key, value){
	if (hasStorage()) {
		localStorage.setItem(window.xuser.id + '|' + key, stringify(value));
	}
}

function getValue(key){
	if (hasStorage()) {
		return eval('__xstorage = ' + localStorage.getItem(window.xuser.id + '|' + key) + ';');
	}
	return null;
}

function deleteValue(key){
	if (hasStorage()) {
		localStorage.removeItem(window.xuser.id + '|' + key);
	}
}

function hasStorage(){
	return _hasStorage;
}

function each(array, fn){
	for(var i = 0; i < array.length; i++){
		var result = fn(array[i], i, array.length);
		if(result == false){
			break;
		}
	}
}

function cloneObject(obj, propertyList){
	var result = {};
	for(var k in obj){
		if(!propertyList || propertyList.indexOf(k) >= 0){
			result[k] = obj[k];
		}
	}
	return result;
}

function stringify(obj){ 
	if(obj == null || obj == undefined){
		return "null";
	}
	var meta = obj._x_meta_object_properties;
	if(meta){
		delete obj._x_meta_object_properties;	
	}	
	var result = JSON.stringify(obj).replace(/_x_value_on_property_/g, "");
	if(meta){
		obj._x_meta_object_properties = meta;		
	}
	return result;		
}

function range(start, end, fn){
	for(var i = start; i < end; i++){
		var result = fn(i);
		if(result == false){
			break;
		}
	}
}

function index(o, l, fi){
	if(o && l){
		if(o.indexOf){
			return o.indexOf(l, fi);
		}else if(o.length){
			if(o instanceof Array){
				for(var i = (fi || 0); i < o.length; i++){
					if(l == o[i]){
						return i;
					}
				}
			}else if(typeof(o) == "string"){
				for(var i = (fi || 0); i < o.length; i++){
					if(o.substring(i, l.length + i) == l){
						return i;
					}
				}
			}
		}
	}
	return -1;
}

function now() {
	Date.now = Date.now || function() {
		return +new Date;
	};
	return Date.now();
}

function generateId() {
	return now() + parseInt(Math.random() * 999999);
}

function trim(s){
	return s.replace(/^\s+|\s+$/g, '');
}

function toHtmlEntities(s){
	return s.replace(/\xE1/g, "&aacute;").replace(/\xE9/g, "&eacute;").replace(/\xED/g, "&iacute;").replace(/\xF3/g, "&oacute;")
		.replace(/\xFA/g, "&uacute;").replace(/\xE3/g, "&atilde;").replace(/\xF5/g, "&otilde;").replace(/\xE7/g, "&ccedil;")
		.replace(/\xEA/g, "&ecirc;").replace(/\xE0/g, "&agrave;").replace(/\xC1/g, "&Aacute;").replace(/\xC9/g, "&Eacute;")
		.replace(/\xCD/g, "&Iacute;").replace(/\xD3/g, "&Oacute;").replace(/\xDA/g, "&Uacute;").replace(/\xC3/g, "&Atilde;")
		.replace(/\xD5/g, "&Otilde;").replace(/\xC7/g, "&Ccedil;").replace(/\xCA/g, "&Ecirc;").replace(/\xC0/g, "&Agrave;");
}

function isChrome() {
	return index(navigator.userAgent.toLowerCase(), 'chrome') > -1;
}

function isIE() {
	return (index(navigator.appVersion, "MSIE") != -1);
}

function getIEVersion(){
	return isIE() ? parseFloat(navigator.appVersion.split("MSIE")[1]) : 999;
}

function createChord(qtd, onFinish){
	var count = 0;
	return {
		notify: function(){
			count++;
			if(count == qtd){
				onFinish();
			}
		},
		getCount: function(){
			return qtd;
		},
		getOnFinishFunction: function(){
			return onFinish;
		}
	};
}

function merge(obj1, obj2){
	for(var k in obj1){
		obj2[k] = obj1[k];
	}
}

function getCookie(cookieName) {
	var splitted = document.cookie.split(';');
	for(var i = 0; i < splitted.length; i++){
		var kv = splitted[i].trim().split('=');
		if(kv[0] == cookieName){
			return kv[1];
		}
	}
	return null;
}

function setCookie(cookieName, value, expiration) {
	  var now = new Date();
	  var time = now.getTime();
	  var expireTime = time + expiration;
	  now.setTime(expireTime);
	  document.cookie = cookieName + '=' + value + ';expires='  +now.toGMTString() + ';path=/';
}

_external(setCookie);
_external(getCookie);
_expose(isChrome);
_expose(isIE);
_expose(getIEVersion);
_external(each);
_external(stringify);
_external(range);
_external(now);
_external(generateId);
_external(index);
_external(trim);
_external(putValue);
_external(getValue);
_external(deleteValue);
_expose(hasStorage);
_external(toHtmlEntities);
_external(cloneObject);
_external(createChord);
_external(merge);
