/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kpax.winfoom.util.pac;

import org.kpax.winfoom.exception.PacFileException;
import org.netbeans.api.scripting.Scripting;
import org.netbeans.core.network.proxy.pac.PacJsEntryFunction;
import org.netbeans.core.network.proxy.pac.PacParsingException;
import org.netbeans.core.network.proxy.pac.PacUtils;
import org.netbeans.core.network.proxy.pac.impl.NbPacHelperMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.net.URI;
import java.util.Objects;

/**
 * A simplified version of {@link org.netbeans.core.network.proxy.pac.impl.NbPacScriptEvaluator} class.
 */
public class NbPacScriptEvaluator {

    private final Logger logger = LoggerFactory.getLogger(NbPacScriptEvaluator.class);

    private final PacScriptEngine scriptEngine;

    public NbPacScriptEvaluator(String pacSourceCode) throws PacParsingException {
        scriptEngine = getScriptEngine(pacSourceCode);
    }

    private PacScriptEngine getScriptEngine(String pacSource) throws PacParsingException {
        try {
            ScriptEngineManager manager = Scripting.newBuilder().build();
            ScriptEngine engine = manager.getEngineByName("Nashorn");
            if (engine == null) {
                throw new PacParsingException("Nashorn engine not found");
            }
            String[] allowedGlobals =
                    ("Object,Function,Array,String,Date,Number,BigInt,"
                            + "Boolean,RegExp,Math,JSON,NaN,Infinity,undefined,"
                            + "isNaN,isFinite,parseFloat,parseInt,encodeURI,"
                            + "encodeURIComponent,decodeURI,decodeURIComponent,eval,"
                            + "escape,unescape,"
                            + "Error,EvalError,RangeError,ReferenceError,SyntaxError,"
                            + "TypeError,URIError,ArrayBuffer,Int8Array,Uint8Array,"
                            + "Uint8ClampedArray,Int16Array,Uint16Array,Int32Array,"
                            + "Uint32Array,Float32Array,Float64Array,BigInt64Array,"
                            + "BigUint64Array,DataView,Map,Set,WeakMap,"
                            + "WeakSet,Symbol,Reflect,Proxy,Promise,SharedArrayBuffer,"
                            + "Atomics,console,performance,"
                            + "arguments").split(",");

            Object cleaner = engine.eval("(function(allowed) {\n"
                    + "   var names = Object.getOwnPropertyNames(this);\n"
                    + "   MAIN: for (var i = 0; i < names.length; i++) {\n"
                    + "     for (var j = 0; j < allowed.length; j++) {\n"
                    + "       if (names[i] === allowed[j]) {\n"
                    + "         continue MAIN;\n"
                    + "       }\n"
                    + "     }\n"
                    + "     delete this[names[i]];\n"
                    + "   }\n"
                    + "})");
            try {
                ((Invocable) engine).invokeMethod(cleaner, "call", null, allowedGlobals);
            } catch (NoSuchMethodException ex) {
                throw new ScriptException(ex);
            }
            engine.eval(pacSource);
            String helperJSScript = HelperScriptFactory.getPacHelperSource();
            logger.debug("PAC Helper JavaScript :\n{}", helperJSScript);
            try {
                ((Invocable) engine).invokeMethod(engine.eval(helperJSScript), "call", null, new NbPacHelperMethods());
            } catch (NoSuchMethodException ex) {
                throw new ScriptException(ex);
            }
            // Do some minimal testing of the validity of the PAC Script.
            PacJsEntryFunction jsMainFunction = testScriptEngine(engine);
            return new PacScriptEngine(engine, jsMainFunction);
        } catch (ScriptException ex) {
            throw new PacParsingException(ex);
        }
    }

    public String findProxyForURL(URI uri) throws PacFileException {
        try {
            Object obj = scriptEngine.findProxyForURL(PacUtils.toStrippedURLStr(uri), uri.getHost());
            return Objects.toString(obj, null);
        } catch (Exception ex) {
            if (ex.getCause() != null) {
                if (ex.getCause() instanceof ClassNotFoundException) {
                    // Is someone trying to break out of the sandbox ?
                    logger.warn("The downloaded PAC script is attempting to access Java class ''{}'' " +
                            "which may be a sign of maliciousness. " +
                            "You should investigate this with your network administrator.", ex.getCause().getMessage());
                }
            }
            // other unforeseen errors
            throw new PacFileException("Error when executing PAC script function " + scriptEngine.getJsMainFunction().getJsFunctionName(), ex);
        }
    }


    /**
     * Test if the main entry point, function FindProxyForURL()/FindProxyForURLEx(),
     * is available.
     */
    private PacJsEntryFunction testScriptEngine(ScriptEngine eng) throws PacParsingException {
        if (isJsFunctionAvailable(eng, PacJsEntryFunction.IPV6_AWARE.getJsFunctionName())) {
            return PacJsEntryFunction.IPV6_AWARE;
        }
        if (isJsFunctionAvailable(eng, PacJsEntryFunction.STANDARD.getJsFunctionName())) {
            return PacJsEntryFunction.STANDARD;
        }
        throw new PacParsingException("Function " + PacJsEntryFunction.STANDARD.getJsFunctionName() +
                " or " + PacJsEntryFunction.IPV6_AWARE.getJsFunctionName() + " not found in PAC Script.");
    }

    private boolean isJsFunctionAvailable(ScriptEngine eng, String functionName) {
        // We want to test if the function is there, but without actually
        // invoking it.
        try {
            Object typeofCheck = eng.eval("(function(name) { return typeof this[name]; })");
            Object type = ((Invocable) eng).invokeMethod(typeofCheck, "call", null, functionName);
            return "function".equals(type);
        } catch (NoSuchMethodException | ScriptException ex) {
            logger.warn("Error on testing if the function is there", ex);
            return false;
        }
    }

    private static class PacScriptEngine {
        private final Invocable invocable;
        private final PacJsEntryFunction jsMainFunction;

        PacScriptEngine(ScriptEngine scriptEngine, PacJsEntryFunction jsMainFunction) {
            this.invocable = (Invocable) scriptEngine;
            this.jsMainFunction = jsMainFunction;
        }

        PacJsEntryFunction getJsMainFunction() {
            return jsMainFunction;
        }

        Object findProxyForURL(String url, String host) throws ScriptException, NoSuchMethodException {
            return invocable.invokeFunction(jsMainFunction.getJsFunctionName(), url, host);
        }
    }

    /**
     * Auto-generates JavaScript source for declaration of
     * helper functions.
     */
    private static class HelperScriptFactory {

        // Netscape functions
        private static final JsHelperFunction[] JS_HELPER_FUNCTIONS_NS = new JsHelperFunction[]{
                new JsHelperFunction("isPlainHostName", new String[]{"host"}, Boolean.class),
                new JsHelperFunction("dnsDomainIs", new String[]{"host", "domain"}, Boolean.class),
                new JsHelperFunction("localHostOrDomainIs", new String[]{"host", "hostdom"}, Boolean.class),
                new JsHelperFunction("isResolvable", new String[]{"host"}, Boolean.class),
                new JsHelperFunction("isInNet", new String[]{"host", "pattern", "mask"}, Boolean.class),
                new JsHelperFunction("dnsResolve", new String[]{"host"}, String.class),
                new JsHelperFunction("myIpAddress", new String[]{}, String.class),
                new JsHelperFunction("dnsDomainLevels", new String[]{"host"}, Integer.class),
                new JsHelperFunction("shExpMatch", new String[]{"str", "shexp"}, Boolean.class),
                new JsHelperFunction("weekdayRange", new String[]{"wd1", "wd2", "gmt"}, Boolean.class),
                new JsHelperFunction("dateRange", new String[]{"day1", "month1", "year1", "day2", "month2", "year2", "gmt"}, Boolean.class),
                new JsHelperFunction("timeRange", new String[]{"hour1", "min1", "sec1", "hour2", "min2", "sec2", "gmt"}, Boolean.class),
        };

        // Microsoft functions
        private static final JsHelperFunction[] JS_HELPER_FUNCTIONS_MS = new JsHelperFunction[]{
                new JsHelperFunction("isResolvableEx", new String[]{"host"}, Boolean.class),
                new JsHelperFunction("isInNetEx", new String[]{"host", "ipPrefix"}, Boolean.class),
                new JsHelperFunction("dnsResolveEx", new String[]{"host"}, String.class),
                new JsHelperFunction("myIpAddressEx", new String[]{}, String.class),
                new JsHelperFunction("sortIpAddressList", new String[]{"ipAddressList"}, String.class),
                new JsHelperFunction("getClientVersion", new String[]{}, String.class)
        };

        // Debug functions (not part of any spec)
        private static final JsHelperFunction[] JS_HELPER_FUNCTIONS_DEBUG = new JsHelperFunction[]{
                new JsHelperFunction("alert", new String[]{"txt"}, Void.class)
        };

        /**
         * Gets JavaScript source with PAC Helper function declarations.
         *
         * @return JavaScript source code that returns a function that delegates
         * to its first argument
         */
        static String getPacHelperSource() {
            StringBuilder sb = new StringBuilder(2000);
            sb.append("(function(self) {\n");
            addFunctionDecls(sb, JS_HELPER_FUNCTIONS_NS);
            addFunctionDecls(sb, JS_HELPER_FUNCTIONS_MS);
            addFunctionDecls(sb, JS_HELPER_FUNCTIONS_DEBUG);
            sb.append("})\n");
            return sb.toString();
        }


        private static void addFunctionDecls(StringBuilder sb, JsHelperFunction[] jsHelperFunctions) {
            for (JsHelperFunction helperFunction : jsHelperFunctions) {
                sb.append("this['");
                sb.append(helperFunction.functionName);
                sb.append("'] = function(");
                addArgList(sb, helperFunction.argList);
                sb.append(") {\n");
                sb.append("    return ");
                boolean encloseReturnValue = false;
                if (Number.class.isAssignableFrom(helperFunction.returnType)) {
                    encloseReturnValue = true;
                    sb.append("Number(");
                } else if (helperFunction.returnType == String.class) {
                    encloseReturnValue = true;
                    sb.append("String(");
                }
                sb.append("self.");
                sb.append(helperFunction.functionName);
                sb.append('(');
                addArgList(sb, helperFunction.argList);
                sb.append(')');
                if (encloseReturnValue) {
                    sb.append(')');
                }
                sb.append(";\n");
                sb.append("}\n\n");
            }
        }

        private static void addArgList(StringBuilder sb, String[] argList) {
            if (argList != null && argList.length > 0) {
                for (int i = 0; i < argList.length; i++) {
                    sb.append(argList[i]);
                    if (i < argList.length - 1) {
                        sb.append(", ");
                    }
                }
            }
        }

        private static class JsHelperFunction {
            final String functionName;
            final String[] argList;
            final Class returnType;

            JsHelperFunction(String functionName, String[] argList, Class returnType) {
                this.functionName = functionName;
                this.argList = argList;
                this.returnType = returnType;
            }

        }

    }

}
