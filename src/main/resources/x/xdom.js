function getElementsByTagNames(){
	var result = [];
	for(var i in arguments){
		var array = document.getElementsByTagName(arguments[i]);
		for(var j = 0; j < array.length; j++){
			result.push(array[j]);	
		}
	}
	return result;
}

function eachInput(each, avoidRegistered){
	var array = getElementsByTagNames('input', 'button', 'select', 'textarea');
	for(var i in array){
		each(array[i]);
	};
	if(!avoidRegistered){
		xcomponents.eachRegisteredObjects(function(obj){
			each(obj);
		});
	}
	xutil.each(getElementsByAttribute('onclick', null), function(el){
		if(array.indexOf(el) == -1){
			each(el);
		}
	});
}

function getInputs(avoidRegistered){
	var array = getElementsByTagNames('input', 'button', 'select', 'textarea');
	if(!avoidRegistered){
		xcomponents.eachRegisteredObjects(function(obj){
			array.push(obj);
		});
	}
	xutil.each(getElementsByAttribute('onclick', null), function(el){
		if(array.indexOf(el) == -1){
			array.push(el);
		}
	});
	return array;
}

function parseAttributes(comp){
	var result = {};
	xutil.each(comp.attributes, function(attr){
		if(typeof(attr.nodeValue) == 'string'){
			result[attr.nodeName] = attr.nodeValue;
		}
	});
	return result;
}

function parseElement(comp){
	var attr = parseAttributes(comp);
	var result = {innerHTML: comp.innerHTML};
	for(var k in attr){
		result[k] = attr[k];
	}
	return result;
}

function createElement(name, attr, parent){
	var el = document.createElement(name);
	for(var k in attr){
		el.setAttribute(k, attr[k]);
	}
	if(parent){
		parent.appendChild(el)
	}
	return el;
}

function getChildNodesByTagName(parent, name, deepSearch, attribute, stopIfFind){
	var nodeList = parent.childNodes;
	var result = [];
	if(nodeList){
		xutil.each(nodeList, function(node){
			if(node){
				var found = false;
				if(node.nodeName && node.nodeName.toUpperCase() == name.toUpperCase()){
					if(!attribute || (node.getAttribute && node.getAttribute(attribute.name) == attribute.value)){
						found = true;
						result.push(node);							
					}
				}
				if(deepSearch && (!stopIfFind || !found)){
					result = result.concat(getChildNodesByTagName(node, name, true));
				}
			}
		});
	}
	return result;
}

function getChildNodesByClassName(parent, name, deepSearch, stopIfFind){
	var nodeList = parent.childNodes;
	var result = [];
	if(nodeList){
		xutil.each(nodeList, function(node){
			if(node){
				var found = false;
				if(node.getAttribute){
					var classes = node.getAttribute("class");
					if(classes && classes.split(" ").indexOf(name) >=0){
						found = true;
						result.push(node);							
					}
					if(deepSearch && (!stopIfFind || !found)){
						result = result.concat(getChildNodesByClassName(node, name, true, stopIfFind));
					}					
				}
			}
		});
	}
	return result;
}
function getChildComponentsByName(parent, name, deepSearch){
	var nodeList = parent.childNodes;
	var result = [];
	if(nodeList){
		xutil.each(nodeList, function(node){
			if(node){
				if(node.nodeName && node.nodeName.toUpperCase() == name.toUpperCase()){
					result.push(parseElement(node));
				}
				if(deepSearch){
					result = result.concat(getChildComponentsByName(node, name, true));
				}
			}
		});
	}
	return result;
}

function findElementsByAttribute(attrName, value, el, like, result){
	var nodeList = el.childNodes;
	if(nodeList){
		xutil.each(nodeList, function(node){
			if(node.nodeType == 1){
				var attrValue = node.getAttribute(attrName);	
				if(attrValue){
					if(result.indexOf(node) < 0 && (value == null || attrValue == value || (like && attrValue.indexOf(value) == 0))){
						result.push(node);
					}
				}
				findElementsByAttribute(attrName, value, node, like, result);
			} 
		});
	}
	return result;
}

function getElementsByAttribute(attrName, value, like){
	var result = [];
	findElementsByAttribute(attrName, value, document.body, like, result);
	return result;
}

function getChildElementsByAttribute(el, attrName, value, like){
	var result = [];
	findElementsByAttribute(attrName, value, el, like, result);
	return result;
}

function updateElementsAttributeValue(el, except){
	el = el || document.body;
	var nodeList = el.childNodes;
	var outnodes = [];
	if(nodeList){
		xutil.each(nodeList, function(node){
			if(node == except){
				return;
			}
			try{
				if(node.nodeType == 1){
					var attrs = node.attributes;
					var outres = [];
					for(var i = 0; i < attrs.length; i++){
						if(attrs[i].name.indexOf("_dynattr_") == 0){
							continue;
						}
						
						var scripts = null;
						var ctx = xdom.findAttributeInParent(el, "_x_ctx");
						if(attrs[i].name == 'xdisablecondition'){
							node.disabled = xobj.evalOnContext(ctx, attrs[i].value) ? true : false;
						}else if(attrs[i].name.indexOf("_outxdynattr_") == 0){
							scripts = attrs[i].value.substring(2, attrs[i].value.length -2);
							var fn = 'function __temp_update_dynval(){return ' + scripts + '};__temp_update_dynval()';
							try{
								outres.push(xobj.evalOnContext(ctx, fn));
							}catch(e){
								xlog.error("Error on update outdynval", e);
							}
						}else{
							var dynval = null;
							var dynname = "_dynattr_" + attrs[i].name;
							dynval = node.getAttribute(dynname);
							if(!dynval){
								dynval = attrs[i].value;
								if(dynval.indexOf("<xattrobject ") < 0){
									continue;
								}
								node.setAttribute(dynname, dynval);
							}
							scripts = dynval.match(/<xattrobject xvar=.*?><\/xattrobject>/g);
							var fn = 'function __temp_update_dynval(){var result=[];';
							
							for(var z = 0; z < scripts.length; z++){
								var s = scripts[z];
								var ictx = s.indexOf(" _x_ctx=");
								var ivar = s.indexOf(" xvar=");
								var scr;
								if(ictx > 0 && ivar < ictx){
									scr = s.substring(19, ictx - 1);
								}else{
									scr = s.substring(19, s.length-16);					
								}
								fn += 'result[' + z + '] = ' + scr.replace(/!#!/g, '"') + ';'
							}
							fn += 'return result;};__temp_update_dynval()';
							try{
								var result = xobj.evalOnContext(ctx, fn);
								for(var z = 0; z < scripts.length; z++){
									dynval = dynval.replace(scripts[z], result[z]);
								}
								node.setAttribute(attrs[i].name, dynval);
							}catch(e){
								xlog.error("Error on update dynval", e);
							}
						}
					}
					if(outres.length > 0){
						outnodes.push([outres, node]);
					}
					updateElementsAttributeValue(node, except);
				} 
			}catch(e){
				xlog.error("Error updating attribute value.", e);
			}
		});
		//update outattributes
		if(outnodes.length > 0){
			for(var i = 0; i < outnodes.length; i++){
				var n = outnodes[i][1];
				var outattrs = outnodes[i][0];
				var notPresent = n.getAttribute("_outxnp_");
				notPresent = notPresent && notPresent.length > 0 ? notPresent.split(" ") : []; 
				var newel = document.createElement(n.nodeName);
				for(var i = 0; i < n.attributes.length; i++){
					if(notPresent.indexOf(n.attributes[i].name) < 0){
						newel.setAttribute(n.attributes[i].name, n.attributes[i].value);						
					}
				}
				var newNotPresent = [];
				for(var i = 0; i < outattrs.length; i++){
					if(outattrs[i]){
						var attr = outattrs[i].split("=");
						newNotPresent.push(attr[0]);
						if(attr.length > 1){
							newel.setAttribute(attr[0], attr[1].substring(1, attr[1].length-1));
						}else{
							newel.setAttribute(attr[0], true);
						}
					}
				}
				if(!notPresent){
					notPresent = newNotPresent;
				}
				newel.setAttribute('_outxnp_', notPresent.join(" "));
				var child = n.childNodes;
				var len = child.length;
				n.parentNode.insertBefore(newel, n);
				for(var j = 0; j < len; j++){
					newel.appendChild(child[0]);
				}
				n.parentNode.removeChild(n);
			}
		}
	}
}

function positionOffset(element) {
    var top = 0, left = 0;
    do {
        top += element.offsetTop  || 0;
        left += element.offsetLeft || 0;
        element = element.offsetParent;
    } while(element);

    return {
        top: top,
        left: left
    };
};

function removeClass(element, className){
	var classes = element.getAttribute("class");
	if(classes){
		classes = classes.split(" ");
		var index = classes.indexOf(className);
		if(index > 0){
			classes.splice(index, 1);
		}
		element.setAttribute("class", classes.join(" "));
	}
}

function addClass(element, className){
	var classes = element.getAttribute("class");
	element.setAttribute("class", (classes ? " " : "") + className);
}

function findAttributeInParent(el, attName){
	if(el == document){
		return null;
	}
	if(el && el.getAttribute(attName)){
		return el.getAttribute(attName);
	}else if(el.parentNode){
		return findAttributeInParent(el.parentNode, attName);
	}else{
		return null;
	}
}


_external(positionOffset);
_external(eachInput);
_external(getInputs);
_external(parseElement);
_external(createElement);
_external(getChildNodesByTagName);
_external(getChildComponentsByName);
_external(getElementsByAttribute);
_expose(findAttributeInParent);
_expose(parseAttributes);
_expose(removeClass);
_expose(addClass);
_expose(getChildNodesByClassName);
_expose(updateElementsAttributeValue);
_external(getChildElementsByAttribute);