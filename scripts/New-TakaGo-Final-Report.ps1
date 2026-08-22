param(
    [string]$OutputPath = "C:\Users\HP\AndroidStudioProjects\takaGo\FYP_TakaGo_FINAL_PROJECT_REPORT.docx"
)

$ErrorActionPreference = 'Stop'
if (Test-Path Alias:H) { Remove-Item Alias:H -Force }
$media = "C:\Users\HP\AndroidStudioProjects\takaGo\FYP_TakaGo_Source_Extracted\word\media"
$androidRoot = "C:\Users\HP\AndroidStudioProjects\takaGo"
$webRoot = "C:\xampp\htdocs\takago"

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$doc = $word.Documents.Add()
$sel = $word.Selection

function Set-Normal {
    $sel.Style = $doc.Styles.Item('Normal'); $sel.Font.Name='Times New Roman'; $sel.Font.Size=12
    $sel.ParagraphFormat.Alignment=3; $sel.ParagraphFormat.LineSpacingRule=1; $sel.ParagraphFormat.LineSpacing=18
    $sel.ParagraphFormat.SpaceAfter=6
}
function P([string]$text, [int]$align=3) { Set-Normal; $sel.ParagraphFormat.Alignment=$align; $sel.TypeText($text); $sel.TypeParagraph() }
function AddReportHeading([string]$text,[int]$level=1) { $sel.Style=$doc.Styles.Item("Heading $level"); $sel.Font.Name='Times New Roman'; $sel.Font.Bold=$true; $sel.TypeText($text); $sel.TypeParagraph() }
Set-Alias -Name H -Value AddReportHeading -Option AllScope -Force
function Page { $sel.InsertBreak(7) }
function Center([string]$text,[int]$size=12,[bool]$bold=$false) { Set-Normal; $sel.ParagraphFormat.Alignment=1; $sel.Font.Size=$size; $sel.Font.Bold=$bold; $sel.TypeText($text); $sel.TypeParagraph() }
function Bullets([string[]]$items) { foreach($x in $items){ Set-Normal; $sel.Range.ListFormat.ApplyBulletDefault(); $sel.TypeText($x); $sel.TypeParagraph(); $sel.Range.ListFormat.RemoveNumbers() } }
function Table([string[]]$headers,[object[]]$rows,[string]$caption){
    Set-Normal; $r=$sel.Range; $t=$doc.Tables.Add($r,$rows.Count+1,$headers.Count); $t.Style='Table Grid'; $t.Rows.Item(1).Range.Bold=$true
    for($c=0;$c-lt $headers.Count;$c++){$t.Cell(1,$c+1).Range.Text=$headers[$c]}
    for($i=0;$i-lt $rows.Count;$i++){for($c=0;$c-lt $headers.Count;$c++){$t.Cell($i+2,$c+1).Range.Text=[string]$rows[$i][$c]}}
    $sel.SetRange($t.Range.End,$t.Range.End); $sel.TypeParagraph(); P $caption 1
}
function Figure([string]$file,[string]$caption,[double]$width=430){
    if(Test-Path $file){ Set-Normal; $sel.ParagraphFormat.Alignment=1; $pic=$sel.InlineShapes.AddPicture($file,$false,$true); if($pic.Width -gt $width){$pic.LockAspectRatio=-1;$pic.Width=$width}; $sel.TypeParagraph(); P $caption 1 }
}
function FieldPage([string]$title,[string]$code){ H $title 1; Set-Normal; $rng=$sel.Range; $null=$doc.Fields.Add($rng,-1,$code,$true); $sel.SetRange($rng.End,$rng.End); Page }
function CodeExcerpt([string]$title,[string]$path,[int]$start,[int]$count){
    H $title 2; P "Source file: $path" 0
    if(Test-Path $path){$lines=Get-Content -LiteralPath $path; $end=[Math]::Min($lines.Count,$start+$count-1); $txt=($lines[($start-1)..($end-1)] -join "`r`n"); Set-Normal; $sel.Font.Name='Consolas';$sel.Font.Size=8;$sel.ParagraphFormat.LineSpacing=10;$sel.ParagraphFormat.Alignment=0;$sel.TypeText($txt);$sel.TypeParagraph()}
}

# Page setup and styles
$doc.PageSetup.TopMargin=72; $doc.PageSetup.BottomMargin=72; $doc.PageSetup.LeftMargin=90; $doc.PageSetup.RightMargin=72
foreach($i in 1..3){$st=$doc.Styles.Item("Heading $i");$st.Font.Name='Times New Roman';$st.Font.Color=0;$st.Font.Bold=$true}
$doc.Styles.Item('Heading 1').Font.Size=14; $doc.Styles.Item('Heading 2').Font.Size=12; $doc.Styles.Item('Heading 3').Font.Size=12

# Title page
Figure "$media\image1.jpeg" "" 110
Center 'THE INSTITUTE OF FINANCE MANAGEMENT (IFM)' 14 $true
Center 'FACULTY OF COMPUTING AND MATHEMATICS (FCM)' 13 $true
Center 'DEPARTMENT OF COMPUTER SCIENCE AND INFORMATION TECHNOLOGY' 12 $true
Center 'BACHELOR OF COMPUTER SCIENCE' 12 $true
Center 'YEAR OF STUDY: THIRD YEAR' 12 $true
Center 'ACADEMIC YEAR: 2025/2026' 12 $true
Center '' 8
Center 'FINAL YEAR PROJECT REPORT' 15 $true
Center 'DEVELOPMENT OF A MOBILE AND WEB-BASED WASTE PICKUP MANAGEMENT SYSTEM: A CASE OF TANZANIA' 14 $true
Center '' 8
Table @('Name','Registration Number') @(
 @('HAPPINESS PIUS PASKALI','IMC/BCS/2312428'),@('REMMY S. MWASELESENDA','IMC/BCS/2325465'),@('FLORINE M. KODDY','IMC/BCS/2324079'),@('ZENA L. JOHN','IMC/BCS/2314906'),@('JOSHUA BERNARD SWAI','IMC/BCS/2325370')) ''
Center 'SUPERVISOR: DR. DAVID MAKOTA' 12 $true
Page

H 'DECLARATION' 1
P 'We declare that this final year project report is our own work. It has not been submitted to any other institution for an academic award. Sources used in this report have been acknowledged.'
P 'Names and signatures of group members:'
foreach($n in @('Happiness Pius Paskali','Remmy S. Mwaselesenda','Florine M. Koddy','Zena L. John','Joshua Bernard Swai')){P "$n  Signature: __________________  Date: ______________" 0}; Page
H 'CERTIFICATION / APPROVAL' 1
P 'I certify that I have supervised this project and recommend this report for examination by the Institute of Finance Management.'
P 'Supervisor: Dr. David Makota' 0; P 'Signature: __________________________  Date: __________________' 0; Page
H 'COPYRIGHT' 1
P 'No part of this report may be reproduced, stored or transmitted without acknowledgement of the authors and the Institute of Finance Management, except for academic use permitted by law.'; Page
H 'DEDICATION' 1
P 'This work is dedicated to our families, lecturers and friends whose support encouraged us throughout the project.'; Page
H 'ACKNOWLEDGEMENTS' 1
P 'We thank Almighty God for giving us health and strength to complete this project. We sincerely thank our supervisor, Dr. David Makota, for guidance, corrections and encouragement. We also thank the Institute of Finance Management, our lecturers, respondents, classmates and families for their support. Their contribution helped us to complete the TakaGo system and this report.'; Page
H 'ABSTRACT' 1
P 'Waste collection in many urban communities is affected by delayed or missed pickups, weak communication, limited tracking and incomplete payment records. This project aimed to develop a mobile and web-based system for requesting, assigning, tracking and managing waste pickup services. Requirements were gathered through a questionnaire answered by 36 respondents, interviews and observation, and were analysed using an Agile approach. The system was implemented using Java and XML in Android Studio, Laravel and PHP for the web application and REST API, MySQL for shared data, and GPS and map services for location functions. TakaGo supports residents, drivers, waste operators, ward administrators, municipal administrators and system administrators. Its main functions include pickup requests, ward detection, driver assignment, status tracking, actual-weight recording, price calculation, payment and cash records, notifications, complaints, receipts, reports and audit records. Functional, validation, role-security and integration tests were carried out by the project team. The results showed that the main modules could exchange shared pickup information and complete the controlled workflow. External User Acceptance Testing was not conducted, and the system remains a prototype. The project concludes that an integrated mobile and web system can improve coordination, visibility and accountability in waste pickup management when supported by reliable internet, GPS data and operational controls.'; Page
FieldPage 'TABLE OF CONTENTS' 'TOC \o "1-3" \h \z \u'
FieldPage 'LIST OF FIGURES' 'TOC \h \z \c "Figure"'
FieldPage 'LIST OF TABLES' 'TOC \h \z \c "Table"'
H 'LIST OF ABBREVIATIONS' 1
Table @('Abbreviation','Meaning') @(@('API','Application Programming Interface'),@('DBMS','Database Management System'),@('DFD','Data Flow Diagram'),@('ERD','Entity Relationship Diagram'),@('FCM','Faculty of Computing and Mathematics'),@('GIS','Geographic Information System'),@('GPS','Global Positioning System'),@('ICT','Information and Communication Technology'),@('IFM','Institute of Finance Management'),@('NFR','Non-Functional Requirement'),@('REST','Representational State Transfer'),@('UML','Unified Modelling Language'),@('UAT','User Acceptance Testing')) 'Table 1: List of abbreviations'; Page

# Chapter One
H 'CHAPTER ONE: INTRODUCTION' 1
H '1.1 Background' 2
P 'Waste management includes the collection, transport, treatment and safe disposal of waste. Proper collection protects public health, reduces blocked drains and uncontrolled dumping, and supports cleaner settlements. Rapid urban growth increases the amount of waste and puts pressure on collection services. The World Bank estimates that global municipal solid waste will continue to rise if prevention and collection systems are not improved (Kaza et al., 2018).'
P 'In Tanzania, local government authorities are responsible for organising solid-waste services under national environmental and local-government laws. Municipalities may provide services directly or work with private waste operators and community organisations. Studies in Dar es Salaam show that private participation can improve service coverage, but weak coordination, affordability, equipment and monitoring remain important challenges (Kaseva and Mbuligwe, 2005; Kirama and Mayo, 2016).'
P 'Traditional collection commonly depends on fixed schedules, phone calls and manual records. A resident may not know when a vehicle will arrive. An operator may find it difficult to select an available driver, follow the job, confirm the amount collected and control cash. Municipal officers may receive reports late. These gaps can cause delayed or missed collection and weak accountability.'
P 'ICT can connect residents, field workers and managers through shared records. Mobile applications can capture requests and GPS coordinates, while web systems can support fleet management, complaints, payments and reports. GPS and GIS methods can identify a user location, estimate distance and relate coordinates to an administrative boundary. TakaGo was developed as an integrated Android and web prototype for the Tanzanian waste-pickup context.'
H '1.2 Problem Statement' 2
P 'Existing waste collection practices may not give residents a simple way to request pickup or see progress. Communication between residents, drivers and operators is often fragmented. Driver assignment may be manual and may not consider availability, approved vehicles, capacity or distance. Tracking is limited, and payments, driver-held cash, complaints and municipal reports may be kept in separate or incomplete records. These problems make it difficult to monitor service quality and accountability. There is therefore a need for a shared system that supports the complete pickup process without replacing the legal and operational responsibilities of municipalities and service providers.'
H '1.3 Main Objective' 2
P 'To develop a mobile and web-based system for requesting, assigning, tracking and managing waste pickup services.'
H '1.4 Specific Objectives' 2
P 'i. To gather requirements for the proposed mobile and web-based waste pickup management system.' 0
P 'ii. To analyse the requirements for the proposed mobile and web-based waste pickup management system.' 0
P 'iii. To design the proposed mobile and web-based waste pickup management system.' 0
P 'iv. To implement the designed mobile and web-based waste pickup management system.' 0
P 'v. To test the developed mobile and web-based waste pickup management system.' 0
H '1.5 Significance of the Project' 2
P 'TakaGo gives residents a clearer way to request collection, follow progress, receive notices and keep payment records. Drivers receive organised assignments and a controlled workflow. Operators can manage drivers, vehicles, cash remittance, complaints and transactions. Ward and municipal administrators can monitor activity within their areas. System administrators can manage access, settings and audit information. These shared functions may improve communication, service coordination, accountability and the cleanliness of communities.'
H '1.6 Project Innovation' 2
P 'The project combines GPS-based ward detection, nearest eligible driver assignment, availability and approved-vehicle checks, estimated waste size, actual weight, price calculation, nearby pickup grouping, shared status, and driver cash-holding control. The innovation is mainly the integration of these functions for local operating roles in one prototype, rather than claiming that each technology is new by itself.'
H '1.7 Scope of the Project' 2
P 'The project covers Android interfaces for residents and drivers and web portals for waste operators, ward administrators, municipal administrators and system administrators. It covers registration and authentication, pickup requests, GPS location, ward detection, assignment, tracking, waste weight, pricing, payments, receipts, notifications, complaints, drivers, vehicles, reports and audit records. It does not cover physical recycling, landfill operation, industrial or hazardous-waste treatment, nationwide deployment, or functions not present in the tested prototype.'
H '1.8 Limitations' 2
P 'The prototype depends on internet access and phone GPS. GPS readings and ward boundaries may be inaccurate, especially indoors or near a boundary. Testing was mainly performed by the project team and did not include external UAT or large-scale municipal use. Production payment credentials and real-world payment settlement were limited. Route optimisation, server capacity, security hardening and scalability were not tested at production scale.'
H '1.9 Organisation of the Report' 2
P 'Chapter One introduces the project. Chapter Two reviews concepts, existing systems and the gap. Chapter Three explains the methodology. Chapter Four presents analysis and design. Chapter Five explains implementation and testing. Chapter Six gives results and discussion. Chapter Seven gives the conclusion, recommendations and future work. References and appendices provide supporting sources, diagrams, screenshots, code, tests, instruments and user guidance.'; Page

# Chapter Two
H 'CHAPTER TWO: LITERATURE REVIEW' 1
H '2.1 Introduction' 2
P 'This chapter reviews digital waste management, mobile and web systems, GPS and GIS, driver assignment, pricing and payments. It also reviews selected existing systems and the Tanzanian context, compares their functions and identifies the gap addressed by TakaGo.'
H '2.2 Theoretical and Conceptual Background' 2
H '2.2.1 Digital Waste Management' 3
P 'Digital waste management uses ICT to record waste services, monitor collections and support decisions. Digital records can show who requested a service, who was assigned, when status changed and whether payment was recorded. Fleet information also helps an operator check vehicles and driver availability.'
H '2.2.2 Mobile and Web-Based Waste Management Systems' 3
P 'Mobile applications support residents and drivers because phones can capture forms, photographs and location data in the field. Web portals provide larger views for administration, search and reporting. A shared API and database allow both interfaces to use the same current record.'
H '2.2.3 GPS and Location-Based Services' 3
P 'GPS provides latitude and longitude. A system can use these coordinates for tracking, distance estimation and route information. Accuracy is affected by buildings, device sensors and network conditions. Location data must therefore be validated and handled carefully (Kaplan and Hegarty, 2017).'
H '2.2.4 GIS and Ward-Based Location Detection' 3
P 'GIS represents places and boundaries. A point-in-polygon test checks whether a GPS point falls inside a ward polygon. Correct results depend on valid coordinates and accurate, current boundary data.'
H '2.2.5 Automated Driver Assignment' 3
P 'Automated assignment may filter drivers by service area, active status, availability, approved vehicle and capacity. Distance can then be used to rank eligible drivers. This reduces manual work, although operators still need controls for exceptions.'
H '2.2.6 Digital Payment and Waste Pricing' 3
P 'Digital payment records improve traceability by linking an amount, method, reference and status to a pickup. Cash needs extra control because a driver may hold money before remittance. Pricing can combine a booking fee, actual weight and chargeable distance. Rates must be transparent and approved by responsible organisations.'
H '2.3 Review of Existing Systems' 2
H '2.3.1 Sensoneo' 3
P 'Sensoneo provides smart-waste tools such as sensor monitoring, route planning and collection management. Its strength is data-driven planning across bins, routes and fleets. Public product information, however, does not describe the exact Tanzania ward-assignment, resident confirmation and local cash-remittance workflow used in TakaGo. It is relevant because it shows the value of real-time data and route planning (Sensoneo, 2026).'
H '2.3.2 Rubicon' 3
P 'Rubicon provides digital waste and recycling services, fleet tools and analytics. Its strength is connecting operational data and reporting for organisations. Its publicly described products are broad enterprise services and do not document the specific TakaGo local ward and cash-control process. It is relevant to management visibility and service coordination (Rubicon, 2026).'
H '2.3.3 AMCS' 3
P 'AMCS offers enterprise software for waste, recycling, transport, billing and customer management. Its strength is a wide set of integrated business functions. Such enterprise scope may require more configuration, infrastructure and organisational capacity than a student prototype. AMCS is relevant to the TakaGo integration of operations, payments and reporting (AMCS, 2026).'
H '2.3.4 Tanzanian Waste-Management Practice' 3
P 'Reliable studies of Dar es Salaam describe municipal responsibility, private-sector participation and service constraints rather than one complete public digital platform. Kaseva and Mbuligwe (2005) found benefits and continuing service challenges after private participation. Kirama and Mayo (2016) also identified institutional and financial issues. These sources support a need for better coordination and records, but they do not prove that no local digital systems exist.'
H '2.4 Comparative Analysis of Existing Systems' 2
$features=@(
 @('Resident pickup request','Limited/publicly unclear','Service platform','Customer tools','Yes'),@('GPS/location','Yes','Yes','Yes','Yes'),@('Driver assignment','Route/fleet focus','Fleet focus','Yes','Yes'),@('Ward detection','Not documented','Not documented','Not documented','Yes'),@('Vehicle management','Yes','Yes','Yes','Yes'),@('Actual waste weight','Selected solutions','Not clear','Yes','Yes'),@('Pricing/payment','Integration dependent','Yes','Yes','Yes'),@('Driver cash control','Not documented','Not documented','Configurable finance','Yes'),@('Complaints','Service tools','Customer service','Customer management','Yes'),@('Municipal reporting','Analytics','Analytics','Analytics','Yes'))
Table @('Feature','Sensoneo','Rubicon','AMCS','TakaGo') $features 'Table 2: Comparison of selected systems'
P 'The comparison shows that established systems are stronger in enterprise analytics, commercial deployment and fleet optimisation. TakaGo is smaller, but it combines resident and driver Android functions with local management roles, ward detection and a clear cash-remittance workflow. “Not documented” means the reviewed public information did not clearly show that exact function; it does not mean the vendor cannot provide it.'
H '2.5 Waste Collection in Tanzania' 2
P 'Tanzanian municipalities work with private operators and communities to collect waste. Challenges reported in the literature include uneven service coverage, limited equipment, financing difficulties, weak enforcement and rapid urban growth. Digital requests, shared status and timely reports are opportunities, but technology must fit local costs, institutions and network conditions (Halla and Majani, 1999; Kirama and Mayo, 2016).'
H '2.6 Research Gap' 2
P 'The reviewed international systems show the benefits of routing, fleet information, customer management and analytics. Tanzanian studies show gaps in collection coverage, coordination, finance and monitoring. Public information reviewed did not show one simple prototype combining resident requests, GPS ward detection, eligible-driver assignment, shared status, actual-weight pricing, driver-held cash control, complaints and role-based local-government reporting for the study context. TakaGo addresses this integration gap while remaining a prototype that still needs field validation.'
H '2.7 Conceptual Framework' 2
Table @('Inputs','Processes','Outputs') @(@('Resident, GPS and waste information','Validate request; detect ward','Recorded pickup and ward'),@('Driver and vehicle information','Check eligibility; compare distance; assign','Assigned driver and tracked pickup'),@('Weight and payment information','Calculate price; process payment/remittance','Final price, receipt and transaction'),@('Complaint and activity data','Notify, report and audit','Notifications, reports and audit records')) 'Figure 1: TakaGo conceptual framework (input-process-output model)'
P 'The framework shows that verified inputs enter controlled processes. The processes produce service and management outputs. Shared identifiers connect each output to the original user, pickup and responsible actor.'
H '2.8 Chapter Summary' 2
P 'The literature shows that digital records, location services, fleet tools and payment records can improve waste operations. Existing systems have strong enterprise functions, while Tanzanian studies identify coordination and monitoring challenges. These findings informed the integrated roles and workflow designed for TakaGo.'; Page

# Chapter Three
H 'CHAPTER THREE: METHODOLOGY' 1
H '3.1 Introduction' 2
P 'Methodology is the organised way used to gather requirements, design, develop and test a system. This chapter compares development methods, explains the selected Agile approach, requirements methods, analysis, feasibility and tools.'
H '3.2 Software Development Methodologies' 2
P 'A software development methodology provides principles and activities for organising a software project (Sommerville, 2016). Four common methods were considered.'
H '3.2.1 Waterfall Methodology' 3
P 'Waterfall follows sequential phases: requirements, design, implementation, testing, deployment and maintenance. It is simple, well documented and easy to track when requirements are stable. Its weaknesses are limited flexibility, late testing and costly changes. TakaGo had changing mobile, API and workflow needs, so strict Waterfall was only moderately suitable.'
H '3.2.2 Spiral Methodology' 3
P 'Spiral development repeats planning, risk analysis, development and evaluation. It supports change and strong risk management for complex systems. It can be difficult, time-consuming and dependent on risk-management experience. It was useful in principle but too heavy for the size and resources of this student project (Boehm, 1988).'
H '3.2.3 Prototyping Methodology' 3
P 'Prototyping creates early versions so users and developers can clarify requirements and interface ideas. It reveals usability problems early. Repeated changes may increase time, users may confuse a prototype with a finished product, and documentation may be neglected. It was useful for screens but not sufficient as the complete management method.'
H '3.2.4 Agile Methodology' 3
P 'Agile develops software in small increments with regular integration, testing and feedback. It is flexible, supports change and identifies problems earlier. It requires team coordination and disciplined documentation, and continued change can make planning difficult. These characteristics matched the connected Android, web, API, database and location components of TakaGo (Beck et al., 2001).'
H '3.3 Comparison of Methodologies' 2
Table @('Methodology','Main strength','Main weakness','Suitability') @(@('Waterfall','Clear sequential process','Difficult to change requirements','Moderate'),@('Spiral','Strong risk management','Complex to manage','Moderate'),@('Prototyping','Clarifies requirements early','Repeated revisions','Moderate/High'),@('Agile','Flexible and iterative','Requires coordination','High')) 'Table 3: Comparison of development methodologies'
P 'Agile provided the best balance for a small team building several connected modules. Waterfall was less suitable for changing requirements, Spiral added management complexity, and Prototyping focused mainly on early models rather than the whole development process.'
H '3.4 Selection and Justification of Agile' 2
P 'Agile was selected because TakaGo contains Android applications, web portals, a Laravel REST API, MySQL, GPS ward detection, driver assignment, payment logic and reports. The team could build one group of functions, connect it to the shared database, test it and then correct the next group. This reduced the risk of finding integration problems only at the end.'
H '3.5 Application of Agile to TakaGo' 2
Table @('Iteration','Main work') @(@('1','Requirements, authentication and user roles'),@('2','Design, pickup request, GPS and ward detection'),@('3','Android/web integration, driver assignment and tracking'),@('4','Weight, pricing, payment, reports, testing and corrections')) 'Table 4: Agile iterations applied to TakaGo'
H '3.6 Requirements Gathering Methods' 2
H '3.6.1 Questionnaire' 3
P 'A questionnaire was used because it could collect similar information from several people. It was answered by 36 respondents and covered collection problems, communication, requests, tracking, payment and useful system functions. The questionnaire is in Appendix G.'
H '3.6.2 Interviews' 3
P 'Semi-structured interviews were reported as part of the requirements work. They were used to understand collection steps and operational problems. Because the source project records do not give a verified count or names of interviewees, this report does not invent them. The interview guide used is presented in Appendix H.'
H '3.6.3 Observation' 3
P 'Observation was used to understand how requests, collection, communication and payment records were handled. It helped the team compare reported needs with practical activities. The report does not claim observation beyond the activities recorded by the project team.'
H '3.7 Requirements Analysis' 2
P 'Questionnaire, interview and observation information was grouped into user needs, functional requirements, non-functional requirements, actors and processes. Similar statements were combined, conflicts were discussed, and requirements were checked against the available time and technology. Detailed requirements are given in Chapter Four.'
H '3.8 Feasibility Analysis' 2
H '3.8.1 Technical Feasibility' 3; P 'Android Studio, Java, Laravel, PHP, MySQL, XAMPP and GPS/map services were available. Test Android phones and computers were sufficient for prototype development.'
H '3.8.2 Operational Feasibility' 3; P 'The designed roles follow normal activities: residents request service, drivers collect waste, operators manage resources, and administrators monitor their areas. Training and reliable procedures would still be needed for real deployment.'
H '3.8.3 Economic Feasibility' 3; P 'The main development tools are open source or freely available for education. Existing computers and phones reduced prototype cost. Production hosting, map usage, payment charges, data bundles, support and weighing equipment would create operating costs.'
H '3.9 Technologies and Tools' 2
Table @('Technology','Purpose') @(@('Android Studio','Build and test mobile applications'),@('Java and XML','Android logic and layouts'),@('Laravel and PHP','Web application and REST API'),@('MySQL','Shared relational database'),@('Blade, HTML, CSS and JavaScript','Web interfaces'),@('Apache/XAMPP','Local development server'),@('GPS and map services','Location, distance, route and ward functions')) 'Table 5: Development technologies and tools'
P 'No project schedule or Gantt chart is included because it is project-management material, not part of this methodology chapter.'; Page

# Chapter Four
H 'CHAPTER FOUR: ANALYSIS AND DESIGN' 1
H '4.1 Introduction' 2; P 'This chapter analyses existing practice and presents the proposed users, requirements, models, database, algorithms, architecture, deployment design and interfaces.'
H '4.2 Analysis of the Existing System' 2
P 'In the existing process, a resident waits for a scheduled collection or contacts an operator. The operator communicates with drivers and chooses a vehicle. Drivers travel to collection points and may receive cash. Residents and municipal officers often depend on calls or later reports to know what happened. Records may be separate, and complaint follow-up may be slow.'
H '4.3 Problems Identified' 2
P 'The literature and gathered requirements identified delayed or missed pickups, poor communication, limited live progress, manual assignment, weak payment and driver-cash monitoring, limited complaint follow-up and incomplete ward or municipal reports.'
H '4.4 Proposed System' 2
P 'TakaGo provides one shared pickup record. A resident submits location and waste details. The backend identifies the ward and selects an eligible driver. Both mobile roles receive status information. The driver records actual weight, the system calculates the price, and the payment and receipt are stored. Web roles manage drivers, vehicles, remittance, complaints, reports and audits.'
H '4.5 Main System Users' 2
Table @('User','Main role') @(@('Resident','Request, track, pay, view receipts and complain'),@('Driver','Accept jobs, navigate, update status, record weight and view transactions'),@('Waste Operator','Manage drivers, vehicles, pickups, payments and remittance'),@('Ward Administrator','Monitor users, pickups and complaints in one ward'),@('Municipal Administrator','Monitor wards and municipality reports'),@('System Administrator','Manage roles, settings, logs, backup and maintenance')) 'Table 6: TakaGo system users'
H '4.6 Functional Requirements' 2
$fr=@('FR01 Authentication and role access','FR02 Create pickup request','FR03 Detect ward from location','FR04 Assign nearest eligible driver','FR05 Track pickup and route','FR06 Record actual waste weight','FR07 Calculate final price','FR08 Record cash or electronic payment','FR09 Generate receipt','FR10 Manage drivers and vehicles','FR11 Record and resolve complaints','FR12 Send and display notifications','FR13 Produce role-based reports','FR14 Store important audit records')
Table @('ID and requirement','Expected system behaviour') ($fr|%{,@($_,($_ -replace '^FR\d+ ',''))}) 'Table 7: Functional requirements'
H '4.7 Non-Functional Requirements' 2
Table @('ID','Requirement') @(@('NFR01 Usability','A trained user should complete a normal request without technical help.'),@('NFR02 Security','Protected functions must require authentication and the correct role.'),@('NFR03 Performance','Normal API requests should normally respond within five seconds on a stable network.'),@('NFR04 Reliability','A valid status change must be stored once and remain consistent across clients.'),@('NFR05 Availability','The service should be available when the server and network are operating.'),@('NFR06 Maintainability','Modules should use clear names, validation and separate responsibilities.'),@('NFR07 Backup','An authorised administrator should be able to create and verify a database backup.'),@('NFR08 Auditability','Important administrative, payment and workflow actions should record actor and time.')) 'Table 8: Non-functional requirements'
H '4.8 System Modelling and Design' 2
H '4.8.1 Use Case Diagram' 3; P 'The use-case diagram introduces the six actors and their permitted functions.'; Figure "$media\image5.png" 'Figure 2: TakaGo use case diagram'
H '4.8.2 Data Flow Diagram' 3; P 'The DFD shows users as external entities, pickup and management work as processes, and the database as shared storage.'; Figure "$media\image6.png" 'Figure 3: TakaGo data flow diagram'
H '4.8.3 Entity Relationship Diagram' 3; P 'The ERD shows keys and relationships among users, wards, vehicles, pickups, payments and other records.'; Figure "$media\image7.png" 'Figure 4: TakaGo entity relationship diagram'
H '4.8.4 Class Diagram' 3; P 'The class diagram represents important application objects, their data and relationships.'; Figure "$media\image8.png" 'Figure 5: TakaGo class diagram'
H '4.8.5 Sequence Diagram' 3
P 'The main sequence is: resident sends a request; API validates and stores it; ward logic identifies the service area; dispatch finds a driver; driver accepts and updates progress; driver records weight; resident confirms; payment is recorded; and the pickup completes. Each response returns the current shared record.'
Table @('Order','Actor/component','Message') @(@('1','Resident app','Submit pickup and coordinates'),@('2','API/database','Validate, detect ward and create pickup'),@('3','Dispatch service','Check drivers and assign eligible nearest driver'),@('4','Driver app','Accept and update controlled status'),@('5','Driver/resident','Record weight and confirm collection/price'),@('6','Payment service','Record payment and complete pickup')) 'Figure 6: Sequence of requesting and completing a pickup'
H '4.8.6 Object Diagram' 3; P 'The object diagram gives example instances, such as one resident, pickup, driver, vehicle and payment, connected at a particular time.'; Figure "$media\image9.png" 'Figure 7: TakaGo object diagram'
H '4.9 Database Design' 2
Table @('Table','Key relationships and purpose') @(@('municipalities','Parent area for wards and users'),@('wards','Belongs to a municipality; referenced by users and pickups'),@('users','Stores identity, role, ward/operator and status'),@('vehicles','Belongs to operator and may be assigned to driver/pickup'),@('pickups','Links resident, driver, operator, ward, vehicle and workflow data'),@('payments','Links pickup and parties; stores amount, method and cash status'),@('complaints','Links reporter and service area'),@('notifications','Links message to receiving user'),@('audit_logs','Stores actor, action, entity, time and change data')) 'Table 9: Main database tables'
P 'Primary keys uniquely identify rows. Foreign keys connect related records and reduce orphan data. Required fields, validation, transactions, unique references and controlled status values support integrity.'
H '4.10 System Algorithms' 2
H '4.10.1 Ward Detection Algorithm' 3; P 'Read GPS coordinates; reject values outside valid latitude and longitude ranges; compare the point with active ward boundaries; select the containing ward; and store its ward ID. If no reliable match is found, the user must correct or retry the location.'
H '4.10.2 Driver Assignment Algorithm' 3; P 'Read the pickup ward; find active and available drivers; require an approved vehicle with sufficient capacity; exclude restricted cash drivers where needed; calculate or obtain distance; rank eligible candidates; assign the nearest candidate inside a database transaction; and notify the parties.'
H '4.10.3 Distance Calculation' 3; P 'TakaGo uses coordinates or route information to estimate distance. Straight-line distance may be calculated with the Haversine formula for filtering, while a map route is better for travel distance. Very small coordinate differences should display zero or “same location” within a defined accuracy threshold.'
H '4.10.4 Price Calculation' 3; P 'Final Price = Booking Fee + Weight Fee + Distance Fee. Weight fee uses chargeable kilograms, the rate per kilogram and a waste-type multiplier where configured. Distance fee applies only to chargeable distance above a free-distance allowance. The result is stored with its component values.'
H '4.10.5 Cash-Control Logic' 3; P 'When the resident confirms cash handover, the payment becomes paid for the resident and held by the driver. The system adds the amount to driver cash held and checks the limit. At the limit, new cash jobs are restricted. After the driver remits cash, the operator confirms collection and the held balance is reduced. The transaction and audit records remain available.'
H '4.11 System Architecture' 2; P 'Android applications call the Laravel REST API. Web browsers use Laravel web routes. Both paths use the shared MySQL database. GPS and map services provide external location and route data.'; Figure "$media\image10.png" 'Figure 8: TakaGo system architecture diagram'
H '4.12 Deployment Design' 2; P 'The prototype deployment contains Android phones, web clients, an Apache/Laravel application and API server, MySQL and internet access to map services. This technical deployment diagram is not a project schedule and does not claim production deployment.'; Figure "$media\image11.png" 'Figure 9: TakaGo deployment design'
H '4.13 User Interface Design' 2; P 'Mobile screens use short tasks and bottom or card navigation for residents and drivers. Web dashboards show role-based summaries, tables, filters and actions. Each role sees only relevant functions. Selected interfaces are in Appendix B.'
H '4.14 Chapter Summary' 2; P 'The analysis connected identified problems to users, measurable requirements, data design, algorithms, architecture and interfaces.'; Page

# Chapter Five
H 'CHAPTER FIVE: IMPLEMENTATION AND TESTING' 1
H '5.1 Introduction' 2; P 'This chapter explains the implemented modules, controls and tests.'
H '5.2 Development Environment' 2; P 'The Android client was built in Android Studio with Java and XML. Laravel/PHP provided the API and web portals, MySQL stored shared data, and Apache/XAMPP provided the local server. HTML, CSS, JavaScript and Blade supported web pages. GPS and map services supported location and routing.'
H '5.3 System Implementation' 2
H '5.3.1 Resident Module' 3; P 'The resident module implements registration, login, profile image, pickup request, coordinates, ward result, tracking, proof images, collection and price confirmation, payment selection, receipt, transaction history, complaints and notifications.'
H '5.3.2 Driver Module' 3; P 'The driver module shows assigned and completed trips, navigation, shared status actions, actual-weight and proof recording, cash information, travelled distance, notifications and transaction history.'
H '5.3.3 Waste Operator Module' 3; P 'The operator portal manages drivers, vehicle approval, pickups, payments, cash held, remittance confirmation, complaints and reports.'
H '5.3.4 Ward Administrator Module' 3; P 'The ward administrator views ward-scoped residents, pickups, vehicles, complaints and service summaries.'
H '5.3.5 Municipal Administrator Module' 3; P 'The municipal administrator views ward performance, municipality totals, vehicles, complaints and reports.'
H '5.3.6 System Administrator Module' 3; P 'The system administrator manages users, roles, settings, system alerts, versions, backups, logs and audit records.'
H '5.4 Authentication and Role-Based Access Control' 2; P 'Login identifies the user and creates an authenticated session or API token. Middleware and controller checks protect functions by role. Server-side validation is used even when the mobile or web interface also validates input.'
H '5.5 GPS and Ward Detection Implementation' 2; P 'The client reads coordinates with permission, validates them and sends them to the API. Ward data is checked by boundary or configured location logic. Invalid or unmatched coordinates return a clear error instead of silently assigning a ward.'
H '5.6 Driver Assignment Implementation' 2; P 'The dispatch service checks active status, availability, operator and ward scope, approved vehicle, capacity, cash restriction and distance. A database transaction and row lock reduce duplicate assignment.'
H '5.7 Pickup Status and Tracking' 2
P 'The implemented controlled lifecycle is: pending → assigned → accepted → on_the_way → arrived → collecting → weight_recorded → resident_confirmation → price_confirmed → payment_pending → paid → completed. Rejection and cancellation are controlled alternatives. The API is the main source of status so resident, driver and web views can refresh the same value.'
H '5.8 Waste Weight and Price Calculation' 2; P 'Estimated size supports planning before collection. The driver records measured kilograms after collection and may attach proof. The server validates weight and calculates booking, weight and distance components. The stored result is displayed to the resident before payment.'
H '5.9 Payment and Cash Control' 2; P 'The resident must choose cash, mobile money or card before payment starts. Electronic payments remain processing until provider verification. For cash, resident confirmation records payment as paid and cash as held by the driver. The operator later confirms remittance as collected. Both mobile roles can view transaction history, while their operational cash status may differ.'
H '5.10 Notifications and Audit Records' 2; P 'Notifications report assignments, status, collection, payment and management events. Audit records store important actor, action, entity, before/after data, IP address and time. They help investigation but do not replace database backup or access security.'
H '5.11 System Testing' 2
H '5.11.1 Testing Approach' 3; P 'The project team used functional, validation, security/role and integration tests. External UAT was not conducted.'
H '5.11.2 Functional Testing' 3; P 'Tests covered login, request, ward detection, assignment, status, weight, pricing, payment, complaints, reports, profile/proof images, trips and transactions.'
H '5.11.3 Input Validation Testing' 3; P 'Tests used missing fields, invalid login details, invalid coordinates, negative or missing weight and invalid status changes.'
H '5.11.4 Security and Role Testing' 3; P 'Protected pages and API actions were tested with missing authentication and incorrect roles. Expected results were 401/403 responses or access denial.'
H '5.11.5 Integration Testing' 3; P 'Integration tests checked Android-to-API requests, API database writes, web visibility, image URLs, shared status, and payment/transaction records.'
H '5.11.6 Test Results Summary' 3
Table @('Area','Result') @(@('Authentication and roles','Passed after access corrections'),@('Pickup and ward data','Passed with coordinate validation'),@('Assignment and status','Passed controlled-transition tests'),@('Weight, pricing and payment','Passed prototype tests'),@('Android/API/web integration','Passed in local test environment'),@('External UAT','Not conducted')) 'Table 10: Test results summary'
P 'Detailed TC01–TC15 cases are presented in Appendix D.'
H '5.12 Chapter Summary' 2; P 'The main role modules and shared workflow were implemented and tested in a prototype environment. Production field and payment testing remain future work.'; Page

# Chapter Six
H 'CHAPTER SIX: RESULTS AND DISCUSSION' 1
H '6.1 Introduction' 2; P 'This chapter presents the main results, discusses them against the literature and evaluates the objectives.'
H '6.2 System Results' 2; P 'The developed prototype provides authentication, pickup requests, ward information, driver assignment, tracking, actual weight, pricing, payments, receipts, proof images, transaction history, complaints, notifications, vehicle and driver management, reports and audit records. Mobile and web modules use shared backend data.'
H '6.3 Testing Results' 2; P 'Team tests found workflow, image-display, transaction, distance and role-access problems during development. Corrections were made to use server records as the source of truth, persist image paths, control status transitions and expose trip/payment information. The final local test run covered the main paths. Large field tests and external UAT were not performed.'
H '6.4 Discussion of Results' 2; P 'The results address the communication and record gaps identified in Chapter One by giving users one linked pickup history. Like Sensoneo, Rubicon and AMCS, TakaGo uses digital operations and reporting. Its project-specific contribution is the combination of ward scope, resident/driver Android roles, actual-weight pricing and driver cash remittance. The prototype is less mature than commercial platforms in routing optimisation, scale, support, sensor integration and proven deployment.'
H '6.5 Achievement of Specific Objectives' 2
Table @('Objective','Status','Evidence') @(@('Gather requirements','Achieved','36-response questionnaire, reported interviews and observation'),@('Analyse requirements','Achieved','Actors and functional/non-functional requirements'),@('Design system','Achieved','UML/DFD/ERD, database, architecture and interfaces'),@('Implement system','Achieved','Android apps, Laravel API/web and MySQL prototype'),@('Test system','Achieved at prototype level','Functional, validation, role and integration tests')) 'Table 11: Achievement of project objectives'
H '6.6 System Strengths' 2; P 'Strengths include one mobile/web data source, ward-based work, role access, eligible-driver checks, actual-weight pricing, cash accountability, transaction history, management reports and auditable changes.'
H '6.7 System Limitations and Challenges' 2; P 'The system depends on internet and GPS quality. Ward-boundary accuracy is not fully verified. Real-world testing, production payment settlement, route optimisation, load testing and long-term security were limited. Phone location error can produce small non-zero distance and ETA values even when two phones are together; a practical accuracy threshold and route refresh are needed.'
H '6.8 Chapter Summary' 2; P 'The prototype achieved the five development objectives and the main workflow, but results should be understood within the stated test and deployment limits.'; Page

# Chapter Seven
H 'CHAPTER SEVEN: CONCLUSION AND RECOMMENDATIONS' 1
H '7.1 Conclusion' 2; P 'This project responded to delayed or missed collection, weak communication, limited tracking and incomplete operational records. It developed a mobile and web-based TakaGo prototype using Android, Laravel, MySQL, GPS and map services. Requirements were gathered and analysed, the system was designed and implemented, and main functions were tested. The prototype shows how shared requests, assignment, status, weight, pricing, payments, complaints, notifications and reports can improve coordination and accountability. It does not prove production performance because external UAT and large-scale deployment were not conducted.'
H '7.2 Recommendations' 2
Bullets @('Verify ward-boundary data with responsible authorities before deployment.','Conduct larger real-world tests with residents, drivers, operators and municipal officers.','Use HTTPS, secure secrets, least-privilege roles, monitoring and regular security review.','Use reliable, calibrated weighing equipment and clear pricing rules.','Reconcile driver-held cash daily and review overdue remittance.','Create and test regular encrypted backups.','Define a GPS accuracy threshold so very close devices show an appropriate distance and ETA.')
H '7.3 Future Work' 2
Bullets @('Complete production Mobile Money and card integration.','Add advanced multi-stop route optimisation.','Add reliable real-time push notifications.','Integrate a supported digital weighing device.','Add advanced service and environmental analytics.','Validate and expand ward data for more municipalities.','Perform scalability, security and recovery testing before wider deployment.'); Page

# References
H 'REFERENCES' 1
$refs=@(
"AMCS. (2026). Waste management software solutions. https://www.amcsgroup.com/",
"Beck, K., et al. (2001). Manifesto for Agile Software Development. https://agilemanifesto.org/",
"Boehm, B. W. (1988). A spiral model of software development and enhancement. Computer, 21(5), 61-72. https://doi.org/10.1109/2.59",
"Google. (2026). Maps Platform documentation. https://developers.google.com/maps/documentation",
"Halla, F., & Majani, B. (1999). Innovative ways for solid waste management in Dar es Salaam. Habitat International, 23(3), 351-361. https://doi.org/10.1016/S0197-3975(98)00057-5",
"ISO. (2011). ISO/IEC 25010:2011 Systems and software quality models. International Organization for Standardization.",
"Kaplan, E. D., & Hegarty, C. J. (Eds.). (2017). Understanding GPS/GNSS: Principles and Applications (3rd ed.). Artech House.",
"Kaseva, M. E., & Mbuligwe, S. E. (2005). Appraisal of solid waste collection following private sector involvement in Dar es Salaam city, Tanzania. Habitat International, 29(2), 353-366. https://doi.org/10.1016/j.habitatint.2003.12.003",
"Kaza, S., Yao, L., Bhada-Tata, P., & Van Woerden, F. (2018). What a Waste 2.0. World Bank. https://doi.org/10.1596/978-1-4648-1329-0",
"Kirama, A., & Mayo, A. W. (2016). Challenges and prospects of private sector participation in solid waste management in Dar es Salaam City, Tanzania. Habitat International, 53, 195-205. https://doi.org/10.1016/j.habitatint.2015.11.014",
"Laravel. (2026). Laravel documentation. https://laravel.com/docs",
"Oracle. (2026). MySQL 8.0 Reference Manual. https://dev.mysql.com/doc/refman/8.0/en/",
"Rubicon. (2026). Technology solutions for waste and recycling. https://www.rubicon.com/",
"Sensoneo. (2026). Smart waste management solutions. https://sensoneo.com/",
"Sommerville, I. (2016). Software Engineering (10th ed.). Pearson.",
"United Republic of Tanzania. (2004). Environmental Management Act, No. 20 of 2004.",
"United Republic of Tanzania. (2009). Environmental Management (Solid Waste Management) Regulations, Government Notice No. 263.",
"United Nations Environment Programme. (2015). Global Waste Management Outlook. UNEP." )
foreach($x in $refs){P $x 0}; Page

# Appendices
H 'APPENDIX A: SYSTEM DESIGN DIAGRAMS' 1
foreach($f in @(@('image5.png','Figure A.1: Use case diagram'),@('image6.png','Figure A.2: Data flow diagram'),@('image7.png','Figure A.3: Entity relationship diagram'),@('image8.png','Figure A.4: Class diagram'),@('image9.png','Figure A.5: Object diagram'),@('image10.png','Figure A.6: System architecture diagram'),@('image11.png','Figure A.7: Deployment diagram'))){Figure "$media\$($f[0])" $f[1] 420}; Page
H 'APPENDIX B: USER INTERFACE SCREENSHOTS' 1
$shots=@(@('image12.png','Resident login interface'),@('image13.jpeg','Resident pickup request interface'),@('image14.jpeg','Driver pickup interface'),@('image15.jpeg','Driver route and navigation interface'),@('image16.jpeg','Waste operator dashboard'),@('image17.jpeg','Municipal administration dashboard'),@('image18.jpeg','System administration dashboard'))
$k=1;foreach($s in $shots){Figure "$media\$($s[0])" "Figure B.${k}: $($s[1])" 390;$k++}; Page
H 'APPENDIX C: TECHNICAL DOCUMENTATION (REAL PROJECT CODE)' 1
P 'The following short excerpts are copied from the actual Android and Laravel/API project files. Long files are shortened to keep the report readable. File paths are shown so the complete code can be inspected in the submitted project.'
CodeExcerpt 'C.1 Android GPS ward detection' "$androidRoot\app\src\main\java\com\takago\app\auth\RegisterActivity.java" 65 65
CodeExcerpt 'C.2 Android driver pickup workflow' "$androidRoot\app\src\main\java\com\takago\app\driver\DriverPickupDetailsActivity.java" 1 120
CodeExcerpt 'C.3 Android resident tracking and shared status' "$androidRoot\app\src\main\java\com\takago\app\resident\ResidentTrackActivity.java" 1 120
CodeExcerpt 'C.4 Laravel controlled pickup transitions' "$webRoot\app\Services\PickupWorkflowService.php" 1 150
CodeExcerpt 'C.5 Laravel driver assignment' "$webRoot\app\Services\PickupDispatchService.php" 1 150
CodeExcerpt 'C.6 Laravel verified payment and cash control' "$webRoot\app\Http\Controllers\VerifiedPaymentController.php" 1 110
CodeExcerpt 'C.7 Laravel audit and backup logic' "$webRoot\app\Http\Controllers\SystemMaintenanceController.php" 1 75
Page
H 'APPENDIX D: TESTING EVIDENCE' 1
$tests=@(@('TC01','Valid login','Dashboard opens and correct role is used','Pass'),@('TC02','Invalid login','Access rejected with clear message','Pass'),@('TC03','Create pickup','Pickup saved with resident and location','Pass'),@('TC04','Invalid coordinates','Request rejected','Pass'),@('TC05','Ward detection','Matching ward ID stored','Pass'),@('TC06','Driver assignment','Eligible nearest driver assigned once','Pass'),@('TC07','Invalid status jump','API rejects transition','Pass'),@('TC08','Accepted-to-collecting path','Shared status follows allowed order','Pass'),@('TC09','Record actual weight','Weight stored and shown to resident','Pass'),@('TC10','Calculate price','Stored components produce final amount','Pass'),@('TC11','Cash payment','Resident paid; driver cash held','Pass'),@('TC12','Cash remittance','Operator confirmation clears held balance','Pass'),@('TC13','Wrong role access','403/access denied','Pass'),@('TC14','Profile/proof image','Allowed image persists and URL reloads','Pass'),@('TC15','Android/API/web integration','Same pickup and transaction visible','Pass'))
Table @('ID','Test data/action','Expected and actual result','Status') $tests 'Table D.1: Detailed functional and integration test cases'
P 'Testing was performed by the project team in the prototype environment. This table is not evidence of external UAT.'; Page
H 'APPENDIX E: PROJECT MANAGEMENT EVIDENCE' 1
P 'This appendix is limited to evidence required by the department, such as approved meeting notes, supervision records and version-control history where available. The project schedule and Gantt chart have been removed as instructed.'; Page
H 'APPENDIX F: USER MANUAL' 1
H 'F.1 Resident' 2; P 'Open the application, register or log in, allow location, check the detected ward, enter waste details and submit. Open Tracking to see the assigned driver and status. After collection, check weight and price, confirm them, choose a payment method and complete payment. Use Transactions for payment history, Notifications for updates, Complaints for a problem and Profile to update account information or image.'
H 'F.2 Driver' 2; P 'Log in and set availability. Open assigned pickups, accept a job and use navigation. Update status in the displayed order. At collection, record actual weight and required proof. View trip history, travelled distance, transactions, cash held and remittance status. Do not mark cash received before the resident hands it over.'
H 'F.3 Waste Operator' 2; P 'Log in to the web portal. Manage drivers and vehicles, monitor pickups, open Transactions, review driver-held cash and confirm remittance only after receiving it. Review complaints and reports.'
H 'F.4 Ward Administrator' 2; P 'Use the ward dashboard to review ward users, pickups, vehicles and complaints. Actions are limited to the assigned ward.'
H 'F.5 Municipal Administrator' 2; P 'Use municipality dashboards and reports to compare wards, review service activity and handle permitted approvals or monitoring actions.'
H 'F.6 System Administrator' 2; P 'Manage users, roles, system settings, logs, backups, versions and maintenance. Review audit information and use least-privilege access.'
H 'F.7 Logout' 2; P 'Select Logout from the profile or navigation menu. Confirm when asked. The local session or token is cleared and the login page opens.'; Page
H 'APPENDIX G: QUESTIONNAIRE' 1
P 'Title: Questionnaire on Waste Pickup Practices and Requirements for TakaGo'
P 'Purpose: To collect academic information about current waste collection problems and expected digital services. Participation is voluntary and answers are used for this project.'
Table @('No.','Question','Response') @(@('1','What is your role?','Resident / Driver / Operator / Officer / Other'),@('2','How often is waste collected in your area?','Daily / Weekly / Irregular / Other'),@('3','Have you experienced delayed or missed collection?','Yes / No'),@('4','How do you request or communicate about collection?','Phone / In person / Schedule / Other'),@('5','Would a mobile pickup request be useful?','Yes / No / Not sure'),@('6','Which information should a request include?','Location / waste type / estimated size / photo / other'),@('7','Would pickup-status tracking be useful?','Yes / No'),@('8','Which payment methods do you prefer?','Cash / Mobile Money / Card'),@('9','Do you need a digital receipt and transaction history?','Yes / No'),@('10','Which collection problem is most important?','Open response'),@('11','Should complaints be submitted through the system?','Yes / No'),@('12','What improvement do you recommend?','Open response')) 'Table G.1: Questionnaire used for requirements gathering'; Page
H 'APPENDIX H: INTERVIEW GUIDE' 1
P 'This guide is included because interviews were reported in the original project methodology. It does not add unverified participant names or counts.'
Bullets @('Please explain how a waste pickup is currently requested and completed.','What causes delayed or missed collection?','How are drivers and vehicles selected?','How are residents informed about progress?','How are waste weight, price and payment recorded?','How is cash collected by drivers monitored and remitted?','How are complaints received and resolved?','Which reports are needed by operators, wards and municipalities?','What problems could affect use of a mobile or web system?','What functions should be given the highest priority?')

# Finish document
foreach($section in $doc.Sections){$footer=$section.Footers.Item(1);$footer.Range.ParagraphFormat.Alignment=1;$null=$footer.PageNumbers.Add()}
$doc.Fields.Update() | Out-Null
$doc.SaveAs2($OutputPath,16)
$pdf=[System.IO.Path]::ChangeExtension($OutputPath,'.pdf'); $doc.ExportAsFixedFormat($pdf,17)
$pages=$doc.ComputeStatistics(2); $words=$doc.ComputeStatistics(0)
$doc.Close($true); $word.Quit()
[Runtime.InteropServices.Marshal]::ReleaseComObject($doc)|Out-Null
[Runtime.InteropServices.Marshal]::ReleaseComObject($word)|Out-Null
"OUTPUT=$OutputPath`nPDF=$pdf`nPAGES=$pages`nWORDS=$words"
