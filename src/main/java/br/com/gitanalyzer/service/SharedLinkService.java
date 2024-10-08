package br.com.gitanalyzer.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.gitanalyzer.analysis.dev_gpt.DevGptSearches;
import br.com.gitanalyzer.analysis.dev_gpt.GitHubCall;
import br.com.gitanalyzer.exceptions.CommandExecutionException;
import br.com.gitanalyzer.exceptions.FetchPageException;
import br.com.gitanalyzer.exceptions.PageJsonProcessingException;
import br.com.gitanalyzer.exceptions.SharedLinkNoCode;
import br.com.gitanalyzer.exceptions.SharedLinkNoConversation;
import br.com.gitanalyzer.exceptions.SharedLinkNotFoundException;
import br.com.gitanalyzer.model.entity.ChatgptConversation;
import br.com.gitanalyzer.model.entity.ConversationTurn;
import br.com.gitanalyzer.model.entity.ErrorLog;
import br.com.gitanalyzer.model.entity.File;
import br.com.gitanalyzer.model.entity.GitRepository;
import br.com.gitanalyzer.model.entity.GitRepositoryFile;
import br.com.gitanalyzer.model.entity.SharedLink;
import br.com.gitanalyzer.model.entity.SharedLinkCommit;
import br.com.gitanalyzer.model.entity.SharedLinkErrorLog;
import br.com.gitanalyzer.model.entity.SharedLinkSearch;
import br.com.gitanalyzer.model.enums.SharedLinkFetchError;
import br.com.gitanalyzer.model.enums.SharedLinkSourceType;
import br.com.gitanalyzer.repository.FileRepository;
import br.com.gitanalyzer.repository.GitRepositoryFileRepository;
import br.com.gitanalyzer.repository.GitRepositoryRepository;
import br.com.gitanalyzer.repository.SharedLinkRepository;
import br.com.gitanalyzer.repository.SharedLinkSearchRepository;
import br.com.gitanalyzer.utils.AsyncUtils;
import br.com.gitanalyzer.utils.Constants;
import br.com.gitanalyzer.utils.FileUtils;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class SharedLinkService {

	@Autowired
	private SharedLinkRepository repository;
	@Autowired
	private GitRepositoryFileRepository gitRepositoryFileRepository;
	@Autowired
	private GitRepositoryRepository gitRepositoryRepository;
	@Autowired
	private FileRepository fileRepository;
	@Autowired
	private SharedLinkRepository sharedLinkRepository;
	@Autowired
	private SharedLinkSearchRepository sharedLinkSearchRepository;
	@Autowired
	private DownloaderService downloaderService;
	@Autowired
	private GitRepositoryService gitRepositoryService;
	@Autowired
	private GitRepositoryVersionService gitRepositoryVersionService;
	@Value("${configuration.github.token}")
	private String token;
	private Pattern pattern = Pattern.compile(Constants.regexOpenAiRegex);

	@Transactional
	public List<GitRepository> saveGitRepositoriesApi() throws InterruptedException, IOException{
		List<GitRepository> repositories = gitRepositoryRepository.findAllWithSharedLinkConversationNotNull();
		ObjectMapper objectMapper = new ObjectMapper();
		ExecutorService executorService = AsyncUtils.getExecutorServiceMax();
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (GitRepository gitRepository : repositories) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() ->{
				try {
					if(gitRepository.getCloneUrl() == null) {
						String[] command = {"curl", "-H", "Authorization: Bearer "+token, 
								Constants.githubApiBaseUrl+"/repos/"+gitRepository.getFullName()};
						String content = GitHubCall.generalCall(command);
						JsonNode rootNode = objectMapper.readTree(content);
						gitRepository.setPrivateRepository(rootNode.get("private")!=null?rootNode.get("private").asBoolean():false);
						gitRepository.setCloneUrl(rootNode.get("clone_url")!=null?rootNode.get("clone_url").asText():null);
						gitRepository.setLanguage(rootNode.get("language")!=null?rootNode.get("language").asText():null);
						gitRepository.setDefaultBranch(rootNode.get("default_branch")!=null?rootNode.get("default_branch").asText():null);
						gitRepository.setSize(rootNode.get("size")!=null?rootNode.get("size").asInt():0);
						gitRepositoryRepository.save(gitRepository);
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}, executorService);
			futures.add(future);
		}
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executorService.shutdown();
		return repositories;
	}

	@Transactional
	public void setConversationSharedLinks() {
		List<SharedLink> sharedLinks = sharedLinkRepository.findByConversationIsNullAndErrorIsNull();
		int i = 0;
		for (int j = 0; j < sharedLinks.size(); j++) {
			SharedLink sharedLink = sharedLinks.get(j);
			try {
				String json = DevGptSearches.getOpenAiJson(sharedLink.getLink());
				ChatgptConversation conversation = DevGptSearches.getConversationOfOpenAiJson(json);
				if (conversation.getConversationTurns().isEmpty()) {
					throw new SharedLinkNoConversation(sharedLink.getLink());
				}
				for(ConversationTurn conversationTurn: conversation.getConversationTurns()) {
					if(conversationTurn.getCodes() != null && !conversationTurn.getCodes().isEmpty()) {
						conversationTurn.setCodes(conversationTurn.getCodes().stream().filter(c -> c.getLanguage()!=null).toList());
					}
				}
				if(conversation.getConversationTurns().stream().anyMatch(c -> c.getCodes() != null && c.getCodes().size() > 0) == false) {
					throw new SharedLinkNoCode(sharedLink.getLink());
				}
				sharedLink.setConversation(conversation);
			}catch (SharedLinkNotFoundException e) {
				e.printStackTrace();
				SharedLinkErrorLog errorLog = new SharedLinkErrorLog(SharedLinkFetchError.NOT_FOUND, new ErrorLog(e.getMessage(), new Date()));
				sharedLink.setError(errorLog);
			} catch (FetchPageException e) {
				SharedLinkErrorLog errorLog = new SharedLinkErrorLog(SharedLinkFetchError.FETCH_ERROR, new ErrorLog(e.getMessage(), new Date()));
				sharedLink.setError(errorLog);
			} catch (PageJsonProcessingException e) {
				SharedLinkErrorLog errorLog = new SharedLinkErrorLog(SharedLinkFetchError.JSON_ERROR, new ErrorLog(e.getMessage(), new Date()));
				sharedLink.setError(errorLog);
			} catch (SharedLinkNoConversation e) {
				e.printStackTrace();
				SharedLinkErrorLog errorLog = new SharedLinkErrorLog(SharedLinkFetchError.LINK_NO_CONVERSATION, new ErrorLog(e.getMessage(), new Date()));
				sharedLink.setError(errorLog);
			} catch (SharedLinkNoCode e) {
				e.printStackTrace();
				SharedLinkErrorLog errorLog = new SharedLinkErrorLog(SharedLinkFetchError.LINK_NO_CODE, new ErrorLog(e.getMessage(), new Date()));
				sharedLink.setError(errorLog);
			} catch (CommandExecutionException e) {
				e.printStackTrace();
				SharedLinkErrorLog errorLog = new SharedLinkErrorLog(SharedLinkFetchError.COMMAND_IO_EXCEPTION, new ErrorLog(e.getMessage(), new Date()));
				sharedLink.setError(errorLog);
			} catch (Exception e) {
				e.printStackTrace();
				SharedLinkErrorLog errorLog = new SharedLinkErrorLog(SharedLinkFetchError.GENERAL_ERROR, new ErrorLog(e.getMessage(), new Date()));
				sharedLink.setError(errorLog);
			}
			sharedLinkRepository.save(sharedLink);
			log.info(i);
			i++;
		}
	}

	private void saveFileSharedLinkItem(JsonNode item, String language) {
		String repositoryLabel = "repository";
		try {
			File file = new File();
			file.setName(item.get("name").asText());
			file.setPath(item.get("path").asText());
			file.setSha(item.get("sha").asText());
			file.setUrl(item.get("url").asText());
			file.setGitUrl(item.get("git_url").asText());
			file.setHtmlUrl(item.get("html_url").asText());
			file.setLanguage(language);	
			fileRepository.save(file);
			JsonNode repositoryNode = item.get(repositoryLabel);
			String repoFullName = repositoryNode.get("full_name").asText();
			GitRepository gitRepository = gitRepositoryRepository.findByFullName(repoFullName);
			if(gitRepository == null) {
				gitRepository = new GitRepository(repositoryNode.get("name").asText(), 
						repositoryNode.get("full_name").asText(), repositoryNode.get("private").asBoolean());
				gitRepositoryRepository.save(gitRepository);
			}
			GitRepositoryFile gitRepositoryFile = new GitRepositoryFile(file, gitRepository);
			JsonNode matchesNode = item.get("text_matches");
			if(!matchesNode.isEmpty()) {
				for (JsonNode matchNode : matchesNode) {
					String fragment = matchNode.get("fragment").asText();
					Matcher matcher = pattern.matcher(fragment);
					while(matcher.find()) {
						String link = matcher.group();
						link = link.trim();
						SharedLink sharedLink = sharedLinkRepository.findByLink(link);
						if(sharedLink == null) {
							sharedLink = new SharedLink();
							sharedLink.setLink(link);
							sharedLink.setTextMatchFragment(fragment);
							sharedLink.setType(SharedLinkSourceType.FILE);
							repository.save(sharedLink);
						}
						boolean present = false;
						for (SharedLinkCommit sharedLinkCommit : gitRepositoryFile.getSharedLinks()) {
							if(sharedLinkCommit.getSharedLink().getLink().equals(link)) {
								present = true;
								break;
							}
						}
						if(!present) {
							gitRepositoryFile.getSharedLinks().add(new SharedLinkCommit(sharedLink));
						}
					}
				}
			}
			gitRepositoryFileRepository.save(gitRepositoryFile);
		}catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage());
		}
	}

	@Transactional
	public List<SharedLink> saveFileSharedLinks() {
		String itemsLabel = "items";
		int perPage = 100;
		ObjectMapper objectMapper = new ObjectMapper();
		List<String> aliases = FileUtils.getProgrammingLanguagesAliasGithub();
		List<SharedLink> sharedLinks = new ArrayList<>();
		for (String language: aliases) {
			int page = 1;
			int indexPage = 1;
			SharedLinkSearch search = new SharedLinkSearch();
			while(indexPage <= page) {
				String[] command = {"curl", "-H", "Accept: application/vnd.github.text-match+json", "-H", "Authorization: Bearer "+token, 
						Constants.githubApiBaseUrl+"/search/code?q=https://chat.openai.com/share/+language:"+language+"&page="+indexPage+"&per_page="+perPage};
				String commandJoined = String.join(" ", command);
				try {
					String content = GitHubCall.searchCall(command);
					JsonNode rootNode = objectMapper.readTree(content);
					if(rootNode.get("total_count") != null) {
						int totalCount = rootNode.get("total_count").asInt();
						search.setTotalNumberOfItems(totalCount);
						search.setSearchType(SharedLinkSourceType.FILE);
						search.setSearchCall(commandJoined);
						sharedLinkSearchRepository.save(search);
						if(totalCount > perPage) {
							page = totalCount/perPage;
							if(totalCount%perPage != 0) {
								page = page + 1;
							}
						}
						if(rootNode.get(itemsLabel) != null && rootNode.get(itemsLabel).size() > 0) {
							for (JsonNode item : rootNode.get(itemsLabel)) {
								saveFileSharedLinkItem(item, language);
							}
						}
						indexPage++;
					}else {
						log.error("Error searching file citation: "+content);
					}
				}catch (Exception e) {
					e.printStackTrace();
					log.error(e.getMessage());
				}
			}
		}
		return sharedLinks;
	}

	@Transactional
	public Object numberSharedLinksPerLanguage() {
		//		int sum = 0;
		//		List<String> languages = fileRepository.findDistinctLanguages();
		//		for (String language : languages) {
		//			System.out.println("========= "+language+" =========");
		//			List<String> links = new ArrayList<>();
		//			List<GitRepositoryFile> gitRepositoryFiles = gitRepositoryFileRepository.findByFileLanguage(language);
		//			int numFiles = 0;
		//			for (GitRepositoryFile gitRepositoryFile : gitRepositoryFiles) {
		//				boolean possuiSharedLink = false;
		//				for (SharedLinkCommit sharedLinkCommit : gitRepositoryFile.getSharedLinks()) {
		//					if(!links.contains(sharedLinkCommit.getSharedLink().getLink()) && sharedLinkCommit.getSharedLink().getConversation() != null) {
		//						links.add(sharedLinkCommit.getSharedLink().getLink());
		//						possuiSharedLink = true;
		//					}
		//				}
		//				if(possuiSharedLink == true) {
		//					numFiles++;
		//				}
		//			}
		//			System.out.println("Number of files: "+numFiles);
		//			System.out.println("Number of shared links: "+links.size());
		//			sum = sum+links.size();
		//			System.out.println();
		//		}
		//		System.out.println(sum);

		List<Integer> distributionOfFiles = new ArrayList<>();
		List<Integer> distributionOfSharedLinks = new ArrayList<>();
		List<GitRepositoryFile> files = gitRepositoryFileRepository.findAll();
		List<GitRepository> repos = gitRepositoryRepository.findAll();
		for(GitRepository repo: repos) {
			List<GitRepositoryFile> filesRepo = files.stream().filter(f -> f.getGitRepository().getId().equals(repo.getId())).toList();
			int numValidFiles = 0;
			for (GitRepositoryFile file : filesRepo) {
				int numValidLinks = 0;
				boolean validFile = false;
				for (SharedLinkCommit sharedLinkCommit : file.getSharedLinks()) {
					if(sharedLinkCommit.getSharedLink().getConversation() != null) {
						validFile = true;
						numValidLinks++;
					}
				}
				if(validFile == true) {
					distributionOfSharedLinks.add(numValidLinks);
					numValidFiles++;
				}
			}
			if(numValidFiles > 0) {
				distributionOfFiles.add(numValidFiles);
			}
		}
		log.info("=== Distribution stats of files per repo ===");
		getStatistcsOfList(distributionOfFiles);
		log.info("=== Distribution stats of shared links per repo===");
		getStatistcsOfList(distributionOfSharedLinks);
		return null;
	}

	private void getStatistcsOfList(List<Integer> distribution) {
		distribution = distribution.stream().sorted().toList();
		DescriptiveStatistics stats = new DescriptiveStatistics();
		distribution.forEach(d -> stats.addValue(d));
		double median = stats.getPercentile(50);
		double q1 = stats.getPercentile(25);
		double q3 = stats.getPercentile(75);
		log.info("Median: " + median);
		log.info("First Quartile (Q1): " + q1);
		log.info("Third Quartile (Q3): " + q3);
	}

	@Transactional
	public void createSharedLinkFull() throws InterruptedException, IOException, URISyntaxException {
		saveFileSharedLinks();
		setConversationSharedLinks();
		saveGitRepositoriesApi();
		List<GitRepository> repositories = downloaderService.cloneRepositoriesWithSharedLinks();
		List<String> repositoriesPath = new ArrayList<>();
		repositories.forEach(r -> repositoriesPath.add(r.getCurrentFolderPath()));
		gitRepositoryService.generateLogFilesRepositoriesPaths(repositoriesPath);
		ExecutorService executorService = AsyncUtils.getExecutorServiceForLogs();
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (GitRepository gitRepository: repositories) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() ->{
				try {
					gitRepositoryVersionService.saveGitRepositoryVersion(gitRepository);
				} catch (Exception e) {
					e.printStackTrace();
					log.error(e.getMessage());
				}
			}, executorService);
			futures.add(future);
		}
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executorService.shutdown();
	}

}
