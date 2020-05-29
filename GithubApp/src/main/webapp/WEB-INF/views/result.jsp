<%@ page import="com.githubApp.model.BugLocator" %>
<%@ page import="java.util.List" %>
<%@ page import="com.githubApp.model.MethodLocator" %><%--
  Created by IntelliJ IDEA.
  User: apple
  Date: 2020/5/9
  Time: 7:33 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html lang="en">
<% BugLocator bugLocator = (BugLocator) request.getAttribute("result"); %>
<% List<MethodLocator> methodLocators = bugLocator.getMethodLocatorList(); %>

<head>
    <meta charset="utf-8">
    <title>Booom!</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <!-- The main CSS file -->
    <link href="/css/main.css" rel="stylesheet">
    <link rel="stylesheet" href="http://cdn.bootcss.com/bootstrap/3.3.0/css/bootstrap.min.css">

    <!-- CSS file for your custom modifications -->
    <!--<link href="css/custom.css" rel="stylesheet">-->

    <!--<link rel="shortcut icon" href="images/favicon.ico">-->

    <!-- HTML5 shim, for IE6-8 support of HTML5 elements. All other JS at the end of file. -->
    <!--[if lt IE 9]>
    <script src="../statics/js/html5shiv.js"></script>
    <![endif]-->
</head>


<!-- Scrollspy set in the body -->
<body id="home" data-spy="scroll" data-target=".main-nav" data-offset="73">

<!--//////////////////////////////////////// NEWSLETTER SECTION ////////////////////////////////////////-->

<section id="newsletter">

    <div class="container">
        <div class="row">

            <div class="col-md-12">
                <!--<p class="lead">Bug Localization!</p>-->
                <h1 style="color: white">BugPecker Tool</h1>
            </div>

            <!--<div class="col-md-6">-->

            <!--////////// Newsletter Form //////////-->
            <!--<form id="newsletter-signup">-->
            <!--<div class="input-group">-->
            <!--<input type="text" name="e-mail" id="e-mail" class="form-control input-hg">-->
            <!--<span class="input-group-btn">-->
            <!--<button class="btn btn-inverse btn-hg" type="submit" name="submit">Sign Up</button>-->
            <!--</span>-->
            <!--</div>&lt;!&ndash; /input-group &ndash;&gt;-->
            <!--</form>-->
            <!--////////// end of Newsletter Form ///////////-->

            <!--<div id="error-info"></div>&lt;!&ndash; Error notification for newsletter signup form &ndash;&gt;-->

            <!--</div>-->

        </div><!-- /row -->
    </div><!-- /container -->

</section>

<!--//////////////////////////////////////// end NEWSLETTER SECTION ////////////////////////////////////////-->

<!--/////////////////////////////////////// FEATURES SECTION ////////////////////////////////////////-->

<section id="features">

    <div class="container">
        <header>
            <h1>Suspicious buggy code of project "<%out.print(bugLocator.getRepoName()); %>"</h1>
            <a href="<%out.print(bugLocator.getIssueUrl()); %>">
                <p class="lead">Bug report title: <%out.print(bugLocator.getIssueTitle()); %> </p>
            </a>
        </header>

        <div class="row">


            <div class="pad">
                <!-- Progress bars -->
                <div class="clearfix">
                    <span class="pull-left">Method Signature</span>
                    <span class="pull-right">Suspicious Probability</span>
                </div>
                <br>
                <%for (int bugSize = 0; bugSize < 5; bugSize++) { %>
                <div class="clearfix">
                    <a href="<%out.print(methodLocators.get(bugSize).getGitUrl()); %>" class="popup">
                        <big class="pull-left"><% out.print(methodLocators.get(bugSize).getMethodName()); %></big>
                        <span style="color:black;width: 240px;height:10px;"><%out.print(methodLocators.get(bugSize).getFullPath()); %></span>
                    </a>
                    <big class="pull-right"><%out.print(methodLocators.get(bugSize).getProbability()); %></big>
                </div>
                <div class="progress xs">
                    <div class="progress-bar progress-bar-green"
                         style="width: <%out.print(Double.valueOf(methodLocators.get(bugSize).getProbability())*100); %>%;"></div>
                </div>
                <%}%>
            </div><!-- /.pad -->
            <%--<center><a href="https://github.com/apps/bugpecker"><button class="btn btn-inverse btn-hg" type="submit" name="submit">Back to github</button></a></center>--%>
        </div>
        <!-- /row -->
    </div><!-- /container -->

</section>

<!--/////////////////////////////////////// end FEATURES SECTION ////////////////////////////////////////-->

</body>
<style>
    span {
        /*margin-top: -40px;*/
        /*margin-left: 20%;*/
        font-size: 20px;
    }

    a.popup {
        position: relative;
    }

    a.popup span {
        display: none;
        position: absolute;
        top: -30px;
        left: 0px;
        z-index: 1;
        padding: 4px;
        /*width: 200px;*/
    }

    a.popup:hover span {
        display: block;
    }

</style>
</html>
